from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    APP_NAME: str = "Printer Service"
    DEFAULT_PRINTER: str = "windows"  # windows | escpos
    PRINTER_NAME: str | None = None
    WINDOWS_PRINT_MODE: str = "auto"  # auto | com | startfile
    ESCPOS_CONNECTION: str = "usb"  # usb | windows
    ESCPOS_USB_VENDOR_ID: str | None = None  # e.g. 0x0483
    ESCPOS_USB_PRODUCT_ID: str | None = None  # e.g. 0x070b
    ESCPOS_USB_TIMEOUT_MS: int = 3000
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

settings = Settings()
