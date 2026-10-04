import itertools
import logging
import math
import os
import tempfile
import threading
from collections import Counter
from datetime import date, datetime, timezone
from typing import Any, Dict, List, Sequence, Union

import joblib
import numpy as np
import pandas as pd
from sklearn.linear_model import PoissonRegressor
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
from sklearn.preprocessing import MultiLabelBinarizer
from xgboost import XGBRegressor

from app.services.model_paths import (
    LEGACY_MLB_PATH,
    LEGACY_MODEL_PATH,
    LEGACY_XGB_MODEL_PATH,
    MLB_PATH,
    MODEL_BUNDLE_PATH,
    MODEL_PATH,
    MODELS_DIR,
)


logger = logging.getLogger(__name__)

model: Any | None = None
mlb: MultiLabelBinarizer | None = None
model_metadata: dict[str, Any] = {}
popular_ingredient_pairs: list[str] = []

_state_lock = threading.RLock()
_training_lock = threading.Lock()

# Цель модели: продажи блюда за день. Если в записях есть блюдо, дни без продаж восстанавливаются нулями.
TARGET_DAILY = "daily_quantity"
TARGET_SALE_DAYS = "daily_quantity_on_sale_days"
SUPPORTED_TARGETS = frozenset({TARGET_DAILY, TARGET_SALE_DAYS})
# Обучение идёт на всей истории; старые дни весят меньше: вес вдвое ниже за каждый год давности.
RECENCY_HALF_LIFE_DAYS = 365.0

_TREE_PARAMS = dict(
    n_estimators=400,
    max_depth=3,
    learning_rate=0.05,
    subsample=0.8,
    colsample_bytree=0.8,
    min_child_weight=5,
    objective="count:poisson",
    random_state=42,
    n_jobs=2,
)


class SalesEnsemble:
    """Прогноз счётных продаж: среднее градиентного бустинга и пуассоновской регрессии.

    Вход тот же, что у прежней модели: признаки ингредиентов, день недели (1–7) и месяц.
    Деревья ловят сочетания ингредиентов, линейная модель устойчивее на новых рецептах;
    на сравнении с прежней XGBoost-регрессией среднее точнее ранжирует новые составы.
    """

    def __init__(self, glm_alpha: float = 1e-3):
        self.glm_alpha = glm_alpha

    @staticmethod
    def _linear_features(features: np.ndarray) -> np.ndarray:
        ingredients = features[:, :-2]
        weekday = np.clip(features[:, -2].astype(int) - 1, 0, 6)
        month = features[:, -1].astype(float)
        return np.hstack([
            ingredients,
            np.eye(7)[weekday],
            np.column_stack([np.sin(2 * np.pi * month / 12), np.cos(2 * np.pi * month / 12)]),
        ])

    def fit(self, features: np.ndarray, target: np.ndarray, sample_weight: np.ndarray | None = None):
        features = np.asarray(features, dtype=float)
        self.trees = XGBRegressor(**_TREE_PARAMS)
        self.trees.fit(features, target, sample_weight=sample_weight, verbose=False)
        self.linear = PoissonRegressor(alpha=self.glm_alpha, max_iter=2000)
        self.linear.fit(self._linear_features(features), target, sample_weight=sample_weight)
        self.n_features_in_ = features.shape[1]
        return self

    def predict_components(self, features: np.ndarray) -> dict[str, np.ndarray]:
        features = np.asarray(features, dtype=float)
        return {
            "trees": np.asarray(self.trees.predict(features), dtype=float),
            "linear": np.asarray(self.linear.predict(self._linear_features(features)), dtype=float),
        }

    def predict(self, features: np.ndarray) -> np.ndarray:
        parts = self.predict_components(features)
        return 0.5 * parts["trees"] + 0.5 * parts["linear"]


class TrainingInProgressError(RuntimeError):
    pass


def _parse_date(value: str | None) -> date | None:
    if not value:
        return None
    try:
        return datetime.fromisoformat(value).date()
    except ValueError:
        return None


def _normalize_ingredients(values: Sequence[str]) -> list[str]:
    if isinstance(values, (str, bytes)) or not isinstance(values, Sequence):
        raise ValueError("ingredients должен быть списком строк")
    normalized = [
        str(value).strip().lower()
        for value in values
        if value is not None and str(value).strip()
    ]
    if not normalized:
        raise ValueError("Список ингредиентов не может быть пустым")
    return list(dict.fromkeys(normalized))


def _snapshot_state() -> tuple[Any, MultiLabelBinarizer, dict[str, Any]]:
    with _state_lock:
        current_model = model
        current_mlb = mlb
        current_metadata = dict(model_metadata)
    if current_model is None or current_mlb is None:
        raise RuntimeError("ML модель не обучена")
    return current_model, current_mlb, current_metadata


def _build_features(
    ingredients: Sequence[str],
    sale_date: str | None,
    *,
    current_model: Any | None = None,
    current_mlb: MultiLabelBinarizer | None = None,
) -> Any:
    if current_model is None or current_mlb is None:
        current_model, current_mlb, _ = _snapshot_state()

    normalized = _normalize_ingredients(ingredients)
    known = {str(value).strip().lower() for value in current_mlb.classes_}
    unknown = sorted(set(normalized) - known)
    if unknown:
        raise ValueError(f"Неизвестные ингредиенты: {', '.join(unknown)}")

    ingredient_features = current_mlb.transform([normalized])
    expected = getattr(current_model, "n_features_in_", None)
    if expected is None:
        raise RuntimeError("Модель не содержит описание входных признаков")

    if ingredient_features.shape[1] == expected:
        return ingredient_features

    parsed_date = _parse_date(sale_date) or date.today()
    date_features = np.array(
        [[float(parsed_date.isoweekday()), float(parsed_date.month)]],
        dtype=float,
    )
    features = np.hstack([ingredient_features, date_features])
    if features.shape[1] != expected:
        raise RuntimeError(
            "Несовместимые артефакты модели: "
            f"ожидалось {expected} признаков, получено {features.shape[1]}"
        )
    return features


def load_model() -> bool:
    """Load a complete model state. Corrupt artifacts leave the service untrained."""
    global model, mlb, model_metadata, popular_ingredient_pairs

    try:
        if MODEL_BUNDLE_PATH.exists():
            bundle = joblib.load(MODEL_BUNDLE_PATH)
            loaded_model = bundle["model"]
            loaded_mlb = bundle["mlb"]
            loaded_metadata = dict(bundle.get("metadata") or {})
            loaded_pairs = list(bundle.get("popularIngredientPairs") or [])
        else:
            model_path = MODEL_PATH if MODEL_PATH.exists() else LEGACY_XGB_MODEL_PATH
            if not model_path.exists():
                model_path = LEGACY_MODEL_PATH
            mlb_path = MLB_PATH if MLB_PATH.exists() else LEGACY_MLB_PATH
            loaded_model = joblib.load(model_path)
            loaded_mlb = joblib.load(mlb_path)
            loaded_metadata = {
                "modelVersion": "legacy",
                "modelType": type(loaded_model).__name__,
                "validationR2": None,
            }
            loaded_pairs = []

        if not hasattr(loaded_mlb, "classes_"):
            raise ValueError("Артефакт MultiLabelBinarizer не обучен")

        with _state_lock:
            model = loaded_model
            mlb = loaded_mlb
            model_metadata = loaded_metadata
            popular_ingredient_pairs = loaded_pairs
        logger.info("ML модель загружена")
        return True
    except FileNotFoundError:
        logger.warning("Модель не загружена: нужны данные для обучения")
    except Exception:
        logger.exception("Не удалось загрузить ML-артефакты")

    with _state_lock:
        model = None
        mlb = None
        model_metadata = {}
        popular_ingredient_pairs = []
    return False


def get_model_info() -> dict[str, Any]:
    with _state_lock:
        loaded = model is not None and mlb is not None
        metadata = dict(model_metadata)
        feature_count = len(mlb.classes_) if mlb is not None else 0
    return {
        "modelLoaded": loaded,
        "modelVersion": metadata.get("modelVersion"),
        "modelType": metadata.get("modelType"),
        "trainedAt": metadata.get("trainedAt"),
        "trainingRecords": metadata.get("trainingRecords", 0),
        "validationRmse": metadata.get("validationRmse"),
        "validationMae": metadata.get("validationMae"),
        "validationR2": metadata.get("validationR2"),
        "ingredientFeatureCount": feature_count,
        "target": metadata.get("target", "legacy_quantity"),
        "warnings": metadata.get("warnings", ["Модель старого формата: требуется переобучение."]),
        "baselineMae": metadata.get("baselineMae"),
        "validationStartDate": metadata.get("validationStartDate"),
    }


def get_confidence_score() -> float | None:
    # R² describes validation fit, not a calibrated probability for a new recipe.
    return None


def get_popular_ingredient_pairs(limit: int = 10) -> list[str]:
    with _state_lock:
        return list(popular_ingredient_pairs[:limit])


def predict_single(ingredients: List[str], sale_date: str | None = None) -> float:
    current_model, current_mlb, _ = _snapshot_state()
    features = _build_features(
        ingredients,
        sale_date,
        current_model=current_model,
        current_mlb=current_mlb,
    )
    prediction = float(current_model.predict(features)[0])
    if not math.isfinite(prediction):
        raise RuntimeError("Модель вернула некорректное числовое значение")
    return max(0.0, prediction)


def predict_batch(rolls: List[Union[List[str], Dict[str, Any]]]) -> list[dict[str, Any]]:
    results: list[dict[str, Any]] = []
    for item in rolls:
        try:
            if isinstance(item, dict):
                ingredients = item.get("ingredients") or item.get("Ingredients") or []
                sale_date = item.get("date")
            else:
                ingredients = item
                sale_date = None
            results.append(
                {
                    "ingredients": ingredients,
                    "predicted_sales": predict_single(ingredients, sale_date),
                }
            )
        except (RuntimeError, ValueError) as exc:
            results.append(
                {
                    "ingredients": (
                        item.get("ingredients") if isinstance(item, dict) else item
                    ),
                    "error": str(exc),
                }
            )
    return results


def _prepare_training_frame(records: list[dict[str, Any]]) -> pd.DataFrame:
    if len(records) < 10:
        raise ValueError("Для обучения требуется не менее 10 записей")

    frame = pd.DataFrame(records)
    required = {"ingredients", "sales", "date"}
    missing = required - set(frame.columns)
    if missing:
        raise ValueError(
            "records должны содержать поля: " + ", ".join(sorted(required))
        )

    dish = pd.Series([None] * len(frame), index=frame.index, dtype=object)
    for column in ("rollName", "dishId"):
        if column in frame.columns:
            values = frame[column].map(lambda value: None if value is None or pd.isna(value) or str(value).strip() == "" else f"{column}:{str(value).strip()}")
            dish = values.where(values.notna(), dish)
    frame = frame.loc[:, ["ingredients", "sales", "date"]].copy()
    frame["dish"] = dish
    frame["ingredients"] = frame["ingredients"].map(_normalize_ingredients)
    frame["sales"] = pd.to_numeric(frame["sales"], errors="coerce")
    frame["date"] = pd.to_datetime(frame["date"], errors="coerce")

    invalid_sales = frame["sales"].isna() | ~np.isfinite(frame["sales"]) | (frame["sales"] < 0)
    if invalid_sales.any():
        raise ValueError("sales должен быть конечным неотрицательным числом")
    if frame["date"].isna().any():
        raise ValueError("date должен быть корректной ISO-датой")
    frame["date"] = frame["date"].dt.normalize()
    if frame["date"].nunique() < 3:
        raise ValueError("Для проверки прогноза нужны продажи минимум за 3 разные даты")
    if frame["sales"].nunique() < 2:
        raise ValueError("Все значения продаж одинаковы: недостаточно разнообразных данных для обучения")

    return frame.sort_values("date", kind="stable").reset_index(drop=True)


def _fill_zero_sale_days(frame: pd.DataFrame) -> tuple[pd.DataFrame, bool]:
    """Добавить нулевые продажи блюда в дни работы кафе между его первой и последней продажей.

    Без этого модель видит только удачные дни и завышает спрос на редко продаваемые блюда.
    Днём работы считается дата, когда было продано хоть что-то. Состав для нулевого дня
    берётся из последней продажи до него (рецепт мог меняться).
    """
    if frame["dish"].isna().any():
        return frame, False

    open_days = np.unique(frame["date"].to_numpy(dtype="datetime64[ns]"))
    additions = []
    for dish, group in frame.groupby("dish", sort=False):
        sold = group.sort_values("date", kind="stable")
        sold_days = np.unique(sold["date"].to_numpy(dtype="datetime64[ns]"))
        span = open_days[(open_days >= sold_days[0]) & (open_days <= sold_days[-1])]
        missing = np.setdiff1d(span, sold_days)
        if len(missing) == 0:
            continue
        recipes = sold.drop_duplicates("date", keep="last").set_index("date")["ingredients"]
        positions = np.searchsorted(recipes.index.to_numpy(dtype="datetime64[ns]"), missing, side="right") - 1
        additions.append(pd.DataFrame({
            "ingredients": [recipes.iloc[max(position, 0)] for position in positions],
            "sales": 0.0,
            "date": missing,
            "dish": dish,
        }))
    if additions:
        frame = pd.concat([frame, *additions], ignore_index=True)
    return frame.sort_values("date", kind="stable").reset_index(drop=True), True


def _recency_weights(dates: pd.Series) -> np.ndarray:
    age_days = (dates.max() - dates).dt.days.to_numpy(dtype=float)
    return np.power(0.5, age_days / RECENCY_HALF_LIFE_DAYS)


def _build_popular_pairs(ingredient_rows: Sequence[Sequence[str]]) -> list[str]:
    counts: Counter[tuple[str, str]] = Counter()
    for ingredients in ingredient_rows:
        counts.update(itertools.combinations(sorted(set(ingredients)), 2))
    return [
        f"{left} + {right}"
        for (left, right), _ in counts.most_common(50)
    ]


def _atomic_dump_bundle(bundle: dict[str, Any]) -> None:
    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    file_descriptor, temporary_path = tempfile.mkstemp(
        prefix="model_bundle_",
        suffix=".tmp",
        dir=MODELS_DIR,
    )
    os.close(file_descriptor)
    try:
        joblib.dump(bundle, temporary_path)
        os.replace(temporary_path, MODEL_BUNDLE_PATH)
    finally:
        if os.path.exists(temporary_path):
            os.unlink(temporary_path)


def train_model(records: list[dict[str, Any]]) -> dict[str, Any]:
    global model, mlb, model_metadata, popular_ingredient_pairs

    if not _training_lock.acquire(blocking=False):
        raise TrainingInProgressError("Обучение модели уже выполняется")

    try:
        prepared = _prepare_training_frame(records)
        frame, zero_days_restored = _fill_zero_sale_days(prepared)
        zero_days_added = len(frame) - len(prepared)
        dates = frame["date"].drop_duplicates().to_list()
        validation_start = dates[max(1, int(len(dates) * 0.8))]
        split_index = int((frame["date"] < validation_start).sum())
        if frame.iloc[:split_index]["sales"].nunique() < 2:
            raise ValueError("Продажи до проверочного периода одинаковы: накопите больше данных")
        local_mlb = MultiLabelBinarizer()
        local_mlb.fit(frame.iloc[:split_index]["ingredients"])
        known = set(local_mlb.classes_)
        ingredient_features = local_mlb.transform(frame["ingredients"].map(
            lambda ingredients: [name for name in ingredients if name in known]
        ))
        date_features = np.column_stack(
            [
                frame["date"].dt.isocalendar().day.astype(float).to_numpy(),
                frame["date"].dt.month.astype(float).to_numpy(),
            ]
        )
        features = np.hstack([ingredient_features, date_features])
        target = frame["sales"].astype(float).to_numpy()
        weights = _recency_weights(frame["date"])

        x_train, x_test = features[:split_index], features[split_index:]
        y_train, y_test = target[:split_index], target[split_index:]

        local_model = SalesEnsemble()
        local_model.fit(x_train, y_train, sample_weight=weights[:split_index])

        predictions = np.maximum(0, local_model.predict(x_test))
        component_mae = {
            name: float(mean_absolute_error(y_test, np.maximum(0, values)))
            for name, values in local_model.predict_components(x_test).items()
        }
        rmse = float(mean_squared_error(y_test, predictions) ** 0.5)
        mae = float(mean_absolute_error(y_test, predictions))
        validation_r2 = (
            float(r2_score(y_test, predictions)) if len(y_test) >= 2 and np.ptp(y_test) > 0 else None
        )
        baseline_mae = float(mean_absolute_error(y_test, np.full_like(y_test, y_train.mean())))
        target_name = TARGET_DAILY if zero_days_restored else TARGET_SALE_DAYS
        warnings = [
            (
                "Дни без продаж восстановлены по дням работы кафе между первой и последней продажей блюда; "
                "дни, когда блюда не было в наличии, тоже считаются нулевыми."
                if zero_days_restored
                else "Прогноз для дней, когда блюдо продавалось: в данных нет блюда, дни без продаж не восстановлены."
            ),
            "Новый состав, граммовки и влияние цены на спрос отдельно не проверены.",
        ]
        if mae >= baseline_mae:
            warnings.append("На проверочном периоде модель не превзошла прогноз средним значением.")
        if validation_r2 is None:
            warnings.append("R² не определён: в проверочном периоде мало данных или продажи одинаковы.")
        if any(set(row) - known for row in frame.iloc[split_index:]["ingredients"]):
            warnings.append("В проверочном периоде встречались ингредиенты, отсутствовавшие в обучении.")
        # Validation uses past-only features; the released model is then fitted on all available days.
        local_mlb = MultiLabelBinarizer()
        features = np.hstack([local_mlb.fit_transform(frame["ingredients"]), date_features])
        local_model = SalesEnsemble().fit(features, target, sample_weight=weights)
        version = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        metadata = {
            "modelVersion": version,
            "modelType": "SalesEnsemble (XGBoost Poisson + Poisson GLM)",
            "trainedAt": datetime.now(timezone.utc).isoformat(),
            "trainingRecords": len(frame),
            "zeroSaleDaysAdded": zero_days_added,
            "trainingStartDate": frame["date"].min().date().isoformat(),
            "trainingEndDate": frame["date"].max().date().isoformat(),
            "recencyHalfLifeDays": RECENCY_HALF_LIFE_DAYS,
            "validationRmse": rmse,
            "validationMae": mae,
            "validationComponentMae": component_mae,
            "validationR2": validation_r2,
            "target": target_name,
            "validationStartDate": validation_start.date().isoformat(),
            "baselineMae": baseline_mae,
            "warnings": warnings,
        }
        pairs = _build_popular_pairs(frame["ingredients"])
        bundle = {
            "schemaVersion": 1,
            "model": local_model,
            "mlb": local_mlb,
            "metadata": metadata,
            "popularIngredientPairs": pairs,
        }
        _atomic_dump_bundle(bundle)

        with _state_lock:
            model = local_model
            mlb = local_mlb
            model_metadata = metadata
            popular_ingredient_pairs = pairs

        logger.info(
            "Training completed: records=%s, RMSE=%.2f, MAE=%.2f, R2=%s",
            len(frame),
            rmse,
            mae,
            validation_r2,
        )
        return {
            "status": "trained",
            "records": len(frame),
            "modelVersion": version,
            "modelType": metadata["modelType"],
            "target": metadata["target"],
            "trainingStartDate": metadata["trainingStartDate"],
            "trainingEndDate": metadata["trainingEndDate"],
            "zeroSaleDaysAdded": metadata["zeroSaleDaysAdded"],
            "warnings": warnings,
            "validation": {
                "rmse": rmse,
                "mae": mae,
                "r2": validation_r2,
                "baselineMae": baseline_mae,
                "componentMae": component_mae,
            },
        }
    finally:
        _training_lock.release()
