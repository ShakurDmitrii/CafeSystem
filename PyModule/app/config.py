from pydantic import BaseSettings

class Settings(BaseSettings):
    APP_NAME: str = "Printer Service"
    DEFAULT_PRINTER: str = "windows"  # windows | escpos

settings = Settings()
