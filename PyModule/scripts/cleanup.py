from pathlib import Path
import shutil


ROOT = Path(__file__).resolve().parents[1]
REMOVED = []


def remove_pycache_dirs() -> None:
    for path in ROOT.rglob("__pycache__"):
        if ".venv" in path.parts:
            continue
        if path.is_dir():
            shutil.rmtree(path, ignore_errors=True)
            REMOVED.append(path)


def remove_generated_files() -> None:
    generated_files = [
        ROOT / "app" / "script" / "ml_training_data.csv",
    ]
    for file_path in generated_files:
        if file_path.exists():
            file_path.unlink()
            REMOVED.append(file_path)


def main() -> None:
    remove_pycache_dirs()
    remove_generated_files()
    print(f"Removed: {len(REMOVED)} items")
    for item in REMOVED:
        print(item)


if __name__ == "__main__":
    main()
