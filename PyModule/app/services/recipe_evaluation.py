"""Per-request evaluation data. Genetic selection/crossover/mutation stay in dish_generator."""
import math
from collections import defaultdict
from statistics import median
from typing import Any

from app.services import service


class RecipeEvaluation:
    def __init__(self, menu_items: list[dict], ingredients: list[dict], constraints: dict[str, Any]):
        self.warnings = {
            "Предварительная рецептура: граммовки сырья требуют проверки поваром; выход готового блюда не рассчитан.",
            "Цена не является прогнозом готовности клиента платить. Упаковка, труд и прочие расходы не включены.",
        }
        samples: dict[str, list[float]] = defaultdict(list)
        for item in menu_items:
            for name, quantity in (item.get("ingredientQuantities") or {}).items():
                if isinstance(quantity, (int, float)) and math.isfinite(quantity) and quantity > 0:
                    samples[name.strip().lower()].append(float(quantity))
        self.weights = {name: median(values) for name, values in samples.items()}
        overrides = constraints.get("ingredientGrams") or {}
        if not isinstance(overrides, dict):
            raise ValueError("ingredientGrams должен быть объектом с граммовками")
        for name, value in overrides.items():
            if not isinstance(value, (int, float)) or not math.isfinite(value) or value <= 0:
                raise ValueError("Граммовка ингредиента должна быть конечным положительным числом")
            self.weights[name.strip().lower()] = float(value)
        prices = [item.get("price") for item in menu_items]
        prices = [float(value) for value in prices if isinstance(value, (int, float))
                  and math.isfinite(value) and value > 0]
        self.reference_price = constraints.get("sellingPrice")
        self.price_source = "requested_price"
        if self.reference_price is None:
            self.reference_price = median(prices) if prices else None
            self.price_source = "menu_median" if prices else "cost_markup"
        elif not isinstance(self.reference_price, (int, float)) or not math.isfinite(self.reference_price) or self.reference_price <= 0:
            raise ValueError("sellingPrice должна быть конечным положительным числом")
        if self.price_source == "menu_median":
            self.warnings.add("Для сравнения вариантов используется единая медианная цена текущего меню.")
        if self.price_source == "cost_markup":
            self.warnings.add("Нет цены для сравнения: наценка служит только ориентиром, прибыль не прогнозируется.")
        self.ingredients = {item.get("name", "").strip().lower(): item for item in ingredients}
        self.predictor = None
        self.model_version = None
        self.cache: dict[tuple[str, ...], float] = {}
        self.constraints = constraints

    def prepare_pool(self, names: list[str]) -> list[str]:
        available = [name for name in names if name in self.weights]
        missing = sorted(set(names) - set(available))
        if missing:
            self.warnings.add("Нет граммовок в техкартах, исключены: " + ", ".join(missing))
        if not available:
            raise ValueError("Для расчёта граммовок нужны заполненные техкарты ингредиентов в граммах")
        if self.constraints.get("useTrainedModelSales", True) is not False:
            try:
                model, binarizer, metadata = service._snapshot_state()
            except RuntimeError:
                self.warnings.add("Модель не обучена: используется эвристическая оценка, без прогноза продаж.")
            else:
                unknown = set(available) - set(binarizer.classes_)
                if metadata.get("target") != "daily_quantity_on_sale_days":
                    self.warnings.add("Модель старого формата: переобучите её. Сейчас используется эвристическая оценка.")
                elif unknown:
                    self.warnings.add("Модель не знает часть пула: все варианты сравниваются эвристически, без прогноза продаж.")
                else:
                    self.model_version = metadata.get("modelVersion")
                    self.warnings.update(metadata.get("warnings") or [])

                    def predict(names: list[str]) -> float:
                        key = tuple(sorted(names))
                        if key not in self.cache:
                            features = service._build_features(names, None, current_model=model, current_mlb=binarizer)
                            value = float(model.predict(features)[0])
                            if not math.isfinite(value):
                                raise RuntimeError("Модель вернула некорректное значение; генерация остановлена")
                            self.cache[key] = max(0.0, value)
                        return self.cache[key]

                    self.predictor = predict
        else:
            self.warnings.add("ML-прогноз отключён: используется эвристическая оценка, без прогноза продаж.")
        return available

    def quantities(self, names: list[str], total_weight: float) -> dict[str, float]:
        total_units = round(total_weight * 10)
        total = sum(self.weights[name] for name in names)
        exact = [total_units * self.weights[name] / total for name in names]
        units = [math.floor(value) for value in exact]
        # Largest remainder preserves the exact 0.1 g total instead of independently rounding each row.
        for index in sorted(range(len(names)), key=lambda i: exact[i] - units[i], reverse=True)[:total_units - sum(units)]:
            units[index] += 1
        if any(value <= 0 for value in units):
            raise ValueError("Вес порции слишком мал для выбранного состава")
        return {name: units[index] / 10 for index, name in enumerate(names)}

    def candidate_warnings(self, names: list[str]) -> list[str]:
        warnings = set(self.warnings)
        fallback = [name for name in names if self.ingredients.get(name, {}).get("costSource") == "default_price_no_stock"]
        if fallback:
            warnings.add("Нет остатка, использована цена по умолчанию: " + ", ".join(fallback))
        return sorted(warnings)
