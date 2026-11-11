from pydantic import Field, SecretStr, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    APP_NAME: str = "CafeHelp Analytics Service"
    JAVA_API_URL: str = "http://localhost:8080"
    INTERNAL_SERVICE_TOKEN: SecretStr
    INTERNAL_API_CONTRACT_VERSION: str = "1"
    JAVA_API_TIMEOUT_SECONDS: float = Field(default=30.0, gt=0, le=120)

    # Распознавание речи для голосового набора заказов (faster-whisper, локально).
    SPEECH_ENABLED: bool = True
    SPEECH_PRELOAD: bool = True
    SPEECH_MODEL: str = "small"
    SPEECH_DEVICE: str = "cpu"
    SPEECH_COMPUTE_TYPE: str = "int8"
    SPEECH_LANGUAGE: str = "ru"
    SPEECH_BEAM_SIZE: int = Field(default=5, ge=1, le=10)
    SPEECH_CPU_THREADS: int = Field(default=0, ge=0, le=64)
    SPEECH_MODEL_DIR: str | None = None
    SPEECH_MAX_AUDIO_BYTES: int = Field(default=5 * 1024 * 1024, gt=0)
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    @field_validator("INTERNAL_SERVICE_TOKEN")
    @classmethod
    def validate_internal_service_token(cls, value: SecretStr) -> SecretStr:
        if len(value.get_secret_value()) < 24:
            raise ValueError("INTERNAL_SERVICE_TOKEN должен содержать не менее 24 символов")
        return value

settings = Settings()
