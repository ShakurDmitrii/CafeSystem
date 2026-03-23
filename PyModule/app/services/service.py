# app/services/ml_service.py
import pandas as pd
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import MultiLabelBinarizer
import joblib
import logging
from typing import List, Optional, Sequence, Union, Any, Dict
from xgboost import XGBRegressor
from datetime import datetime, date

from app.services.model_paths import (
    MODELS_DIR,
    MODEL_PATH,
    MLB_PATH,
    LEGACY_MODEL_PATH,
    LEGACY_XGB_MODEL_PATH,
    LEGACY_MLB_PATH,
)

logger = logging.getLogger(__name__)

# Глобальные переменные модели
model = None
mlb = None

def _parse_date(value: Optional[str]) -> Optional[date]:
    if not value:
        return None
    try:
        # Accept both "YYYY-MM-DD" and full ISO strings.
        return datetime.fromisoformat(value).date()
    except ValueError:
        try:
            return datetime.strptime(value, "%Y-%m-%d").date()
        except ValueError:
            return None


def _build_features(ingredients: Sequence[str], sale_date: Optional[str]) -> "Any":
    """
    Build feature vector matching the trained model.
    Always: MultiLabelBinarizer(ingredients)
    Optionally: [dayOfWeek, month] if model expects them.
    """
    if mlb is None:
        raise RuntimeError("ML модель не обучена")

    X_ing = mlb.transform([list(ingredients)])

    expected = getattr(model, "n_features_in_", None)
    if expected is None:
        # Fallback: old/unknown model; try ingredients-only.
        return X_ing

    if X_ing.shape[1] == expected:
        return X_ing

    # Try with extra date features.
    d = _parse_date(sale_date) or date.today()
    dow = float(d.isoweekday())  # 1..7
    mon = float(d.month)         # 1..12
    import numpy as np
    X = np.hstack([X_ing, np.array([[dow, mon]], dtype=float)])
    if X.shape[1] == expected:
        return X

    # Last resort: if model was trained without date features but expects fewer due to mismatch,
    # try trimming (shouldn't normally happen).
    if expected < X_ing.shape[1]:
        return X_ing[:, :expected]

    raise RuntimeError(f"Несовместимые фичи: model expects {expected}, got {X_ing.shape[1]} (or {X.shape[1]} with date features)")


def load_model():
    """Загружает модель и mlb из файлов, если они есть"""
    global model, mlb
    try:
        model_path = MODEL_PATH if MODEL_PATH.exists() else LEGACY_XGB_MODEL_PATH
        if not model_path.exists():
            model_path = LEGACY_MODEL_PATH

        mlb_path = MLB_PATH if MLB_PATH.exists() else LEGACY_MLB_PATH

        model = joblib.load(model_path)
        mlb = joblib.load(mlb_path)
        logger.info("ML модель загружена")
    except FileNotFoundError:
        logger.warning("Модель не загружена, нужны данные для обучения")


def predict_single(ingredients: List[str], sale_date: Optional[str] = None) -> float:
    """Предсказание для одного ролла"""
    if model is None:
        raise RuntimeError("ML модель не обучена")
    X = _build_features(ingredients, sale_date)
    return float(model.predict(X)[0])


def predict_batch(rolls: List[Union[List[str], Dict[str, Any]]]) -> list:
    """Пакетное предсказание"""
    results = []
    for item in rolls:
        try:
            if isinstance(item, dict):
                ingredients = item.get("ingredients") or item.get("Ingredients") or []
                sale_date = item.get("date")
            else:
                ingredients = item
                sale_date = None

            prediction = predict_single(ingredients, sale_date)
            results.append({
                "ingredients": ingredients,
                "predicted_sales": float(prediction)
            })
        except Exception as e:
            results.append({
                "ingredients": item.get("ingredients") if isinstance(item, dict) else item,
                "error": str(e)
            })
    return results


def train_model(records: list):
    global model, mlb
    if not records:
        raise ValueError("Нет данных для обучения")

    df = pd.DataFrame(records)
    if "ingredients" not in df.columns or "sales" not in df.columns:
        raise ValueError("records должны содержать поля 'ingredients' и 'sales'")

    # Преобразуем ингредиенты в бинарные фичи
    mlb = MultiLabelBinarizer()
    X_ingredients = mlb.fit_transform(df['ingredients'])

    # Стабильные дополнительные признаки из даты (или 0, если даты нет)
    import numpy as np
    if "date" in df.columns:
        parsed = pd.to_datetime(df["date"], errors="coerce")
        if hasattr(parsed.dt, "isocalendar"):
            # pandas >= 1.1
            iso = parsed.dt.isocalendar()
            day_of_week = iso["day"].fillna(0).astype(float)
            month = parsed.dt.month.fillna(0).astype(float)
        else:
            # Fallback for older pandas
            day_of_week = (parsed.dt.dayofweek + 1).fillna(0).astype(float)
            month = parsed.dt.month.fillna(0).astype(float)
    else:
        day_of_week = pd.Series([0.0] * len(df))
        month = pd.Series([0.0] * len(df))

    extra_features = np.vstack([day_of_week.to_numpy(), month.to_numpy()]).T
    X = np.hstack([X_ingredients, extra_features])

    y = df['sales']

    # Разделяем на train/test
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

    # XGBoost регрессор
    model = XGBRegressor(
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
    model.fit(
        X_train,
        y_train,
        eval_set=[(X_test, y_test)],
        verbose=False,
    )

    # Можно проверить метрику
    rmse = ((model.predict(X_test) - y_test) ** 2).mean() ** 0.5
    print(f"Training completed, RMSE on test: {rmse:.2f}")

    # Сохраняем модель и mlb в единое место хранения.
    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    joblib.dump(model, MODEL_PATH)
    joblib.dump(mlb, MLB_PATH)

    return {"status": "trained", "records": len(records), "rmse": rmse}
