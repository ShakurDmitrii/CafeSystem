from pathlib import Path


PYMODULE_DIR = Path(__file__).resolve().parents[2]
MODELS_DIR = PYMODULE_DIR / "models"

# Legacy location support for older local files in PyModule root.
LEGACY_MODEL_PATH = PYMODULE_DIR / "model.pkl"
LEGACY_XGB_MODEL_PATH = PYMODULE_DIR / "xgb_model.pkl"
LEGACY_MLB_PATH = PYMODULE_DIR / "mlb.pkl"

MODEL_PATH = MODELS_DIR / "xgb_model.pkl"
MLB_PATH = MODELS_DIR / "mlb.pkl"
