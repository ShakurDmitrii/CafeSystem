from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    APP_NAME: str = "Printer Service"
    DEFAULT_PRINTER: str = "windows"  # windows | escpos
    PRINTER_NAME: str | None = None
    WINDOWS_PRINT_MODE: str = "auto"  # auto | com | startfile
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

settings = Settings()
