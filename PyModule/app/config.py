from pydantic import SecretStr, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    APP_NAME: str = "CafeHelp Analytics Service"
    JAVA_API_URL: str = "http://localhost:8080"
    INTERNAL_SERVICE_TOKEN: SecretStr
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    @field_validator("INTERNAL_SERVICE_TOKEN")
    @classmethod
    def validate_internal_service_token(cls, value: SecretStr) -> SecretStr:
        if len(value.get_secret_value()) < 24:
            raise ValueError("INTERNAL_SERVICE_TOKEN должен содержать не менее 24 символов")
        return value

settings = Settings()
