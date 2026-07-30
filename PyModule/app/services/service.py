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
        if str(value).strip()
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
    }


def get_confidence_score() -> float | None:
    score = get_model_info().get("validationR2")
    if score is None or not math.isfinite(float(score)):
        return None
    return round(max(0.0, min(1.0, float(score))), 4)


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
    return float(current_model.predict(features)[0])


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

    frame = frame.loc[:, ["ingredients", "sales", "date"]].copy()
    frame["ingredients"] = frame["ingredients"].map(_normalize_ingredients)
    frame["sales"] = pd.to_numeric(frame["sales"], errors="coerce")
    frame["date"] = pd.to_datetime(frame["date"], errors="coerce")

    invalid_sales = frame["sales"].isna() | ~np.isfinite(frame["sales"]) | (frame["sales"] < 0)
    if invalid_sales.any():
        raise ValueError("sales должен быть конечным неотрицательным числом")
    if frame["date"].isna().any():
        raise ValueError("date должен быть корректной ISO-датой")

    return frame.sort_values("date", kind="stable").reset_index(drop=True)


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
        frame = _prepare_training_frame(records)
        local_mlb = MultiLabelBinarizer()
        ingredient_features = local_mlb.fit_transform(frame["ingredients"])
        date_features = np.column_stack(
            [
                frame["date"].dt.isocalendar().day.astype(float).to_numpy(),
                frame["date"].dt.month.astype(float).to_numpy(),
            ]
        )
        features = np.hstack([ingredient_features, date_features])
        target = frame["sales"].astype(float).to_numpy()

        split_index = max(1, min(len(frame) - 2, int(len(frame) * 0.8)))
        x_train, x_test = features[:split_index], features[split_index:]
        y_train, y_test = target[:split_index], target[split_index:]

        local_model = XGBRegressor(
            n_estimators=800,
            max_depth=6,
            learning_rate=0.03,
            subsample=0.8,
            colsample_bytree=0.8,
            reg_alpha=0.0,
            reg_lambda=1.0,
            objective="reg:squarederror",
            eval_metric="rmse",
            random_state=42,
        )
        local_model.fit(
            x_train,
            y_train,
            eval_set=[(x_test, y_test)],
            verbose=False,
        )

        predictions = local_model.predict(x_test)
        rmse = float(mean_squared_error(y_test, predictions) ** 0.5)
        mae = float(mean_absolute_error(y_test, predictions))
        validation_r2 = (
            float(r2_score(y_test, predictions)) if len(y_test) >= 2 else None
        )
        version = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        metadata = {
            "modelVersion": version,
            "modelType": type(local_model).__name__,
            "trainedAt": datetime.now(timezone.utc).isoformat(),
            "trainingRecords": len(frame),
            "validationRmse": rmse,
            "validationMae": mae,
            "validationR2": validation_r2,
            "target": "quantity",
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
            "target": "quantity",
            "validation": {
                "rmse": rmse,
                "mae": mae,
                "r2": validation_r2,
            },
        }
    finally:
        _training_lock.release()
