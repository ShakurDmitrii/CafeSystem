import random
from dataclasses import dataclass
from typing import Any

DEFAULT_TOTAL_WEIGHT_GRAMS = 140.0


@dataclass
class Candidate:
    ingredients: list[str]
    fitness: float
    predicted_sales: float
    estimated_cost: float
    recommended_price: float
    estimated_profit: float
    novelty_score: float
    generation_found: int


def _safe_float(value: Any, default: float = 0.0) -> float:
    try:
        if value is None:
            return default
        if isinstance(value, str):
            value = value.replace(",", ".").strip()
        return float(value)
    except Exception:
        return default


def _safe_int(value: Any, default: int = 0) -> int:
    try:
        if value is None:
            return default
        if isinstance(value, str):
            value = value.replace(",", ".").strip()
        return int(float(value))
    except Exception:
        return default


def _normalize_ingredients(raw: Any) -> list[str]:
    if not isinstance(raw, list):
        return []
    result: list[str] = []
    for item in raw:
        if item is None:
            continue
        value = str(item).strip().lower()
        if value:
            result.append(value)
    return list(dict.fromkeys(result))


def _normalize_unit(value: Any) -> str:
    if value is None:
        return "kg"
    unit = str(value).strip().lower()
    return unit


def _grams_per_unit(unit: str) -> float:
    kg_aliases = {"kg", "кг", "kilogram", "kilograms", "килограмм", "килограммы"}
    g_aliases = {"g", "гр", "г", "gram", "grams", "грамм", "граммы"}
    l_aliases = {"l", "л", "liter", "liters", "литр", "литры"}
    ml_aliases = {"ml", "мл", "milliliter", "milliliters"}
    piece_aliases = {"pc", "pcs", "piece", "pieces", "шт", "штука", "штук"}

    if unit in kg_aliases:
        return 1000.0
    if unit in g_aliases:
        return 1.0
    if unit in l_aliases:
        return 1000.0
    if unit in ml_aliases:
        return 1.0
    if unit in piece_aliases:
        return 1.0
    return 1000.0


def _ingredient_pool(ingredients: list[dict]) -> tuple[list[str], dict[str, float], dict[str, float], dict[str, str]]:
    names: list[str] = []
    costs: dict[str, float] = {}
    grams_per_unit: dict[str, float] = {}
    units: dict[str, str] = {}
    for item in ingredients or []:
        if not isinstance(item, dict):
            continue
        name = str(item.get("name", "")).strip().lower()
        if not name:
            continue
        unit = _normalize_unit(item.get("unit"))
        cost = _safe_float(item.get("costPerUnit"), 0.0)
        if cost <= 0:
            cost = 10.0
        names.append(name)
        costs[name] = cost
        units[name] = unit
        grams_per_unit[name] = _grams_per_unit(unit)
    unique_names = list(dict.fromkeys(names))
    return unique_names, costs, grams_per_unit, units


def _build_sales_weights(sales_records: list[dict]) -> dict[str, float]:
    weights: dict[str, float] = {}
    for record in sales_records or []:
        if not isinstance(record, dict):
            continue
        qty = _safe_int(record.get("quantity"), 0)
        if qty <= 0:
            qty = 1
        for ingredient in _normalize_ingredients(record.get("ingredients")):
            weights[ingredient] = weights.get(ingredient, 0.0) + float(qty)
    return weights


def _existing_dish_sets(menu_items: list[dict]) -> list[set[str]]:
    result: list[set[str]] = []
    for item in menu_items or []:
        if not isinstance(item, dict):
            continue
        ing = set(_normalize_ingredients(item.get("ingredients")))
        if ing:
            result.append(ing)
    return result


def _jaccard(a: set[str], b: set[str]) -> float:
    if not a and not b:
        return 0.0
    union = len(a | b)
    if union == 0:
        return 0.0
    return len(a & b) / union


def _max_similarity(current_set: set[str], existing_dishes: list[set[str]]) -> float:
    if not existing_dishes or not current_set:
        return 0.0
    return max(_jaccard(current_set, dish) for dish in existing_dishes)


def _is_too_similar(current_set: set[str], existing_dishes: list[set[str]], threshold: float) -> bool:
    if threshold <= 0:
        return False
    return _max_similarity(current_set, existing_dishes) >= threshold


def _evaluate_candidate(
    ingredients: list[str],
    ingredient_costs: dict[str, float],
    ingredient_grams_per_unit: dict[str, float],
    sales_weights: dict[str, float],
    existing_dishes: list[set[str]],
    markup: float,
    generation_idx: int,
    total_weight_grams: float,
) -> Candidate:
    unique_ingredients = list(dict.fromkeys(ingredients))
    current_set = set(unique_ingredients)

    per_ingredient_grams = total_weight_grams / max(1, len(unique_ingredients))
    estimated_cost = sum(
        ingredient_costs.get(ing, 10.0) * (per_ingredient_grams / max(1.0, ingredient_grams_per_unit.get(ing, 1000.0)))
        for ing in unique_ingredients
    )
    recommended_price = round(max(estimated_cost * markup, estimated_cost + 20.0), 2)
    margin = max(recommended_price - estimated_cost, 0.0)

    popularity = sum(sales_weights.get(ing, 0.0) for ing in unique_ingredients)
    predicted_sales = round(8.0 + popularity / 10.0, 2)

    max_similarity = _max_similarity(current_set, existing_dishes)
    novelty_score = round(max(0.0, 1.0 - max_similarity), 4)

    estimated_profit = round(predicted_sales * margin, 2)
    fitness = round(estimated_profit * (0.6 + 0.4 * novelty_score), 4)

    return Candidate(
        ingredients=unique_ingredients,
        fitness=fitness,
        predicted_sales=predicted_sales,
        estimated_cost=round(estimated_cost, 2),
        recommended_price=recommended_price,
        estimated_profit=estimated_profit,
        novelty_score=novelty_score,
        generation_found=generation_idx,
    )


def _make_random_candidate(
    ingredient_names: list[str],
    min_ingredients: int,
    max_ingredients: int,
    must_include: list[str],
    excluded: set[str],
) -> list[str]:
    allowed = [n for n in ingredient_names if n not in excluded]
    if not allowed:
        return []

    min_len = max(min_ingredients, len(must_include))
    max_len = max(min_len, max_ingredients)
    target_len = random.randint(min_len, max_len)

    base = [x for x in must_include if x in allowed]
    remaining = [x for x in allowed if x not in base]
    random.shuffle(remaining)
    result = base + remaining[: max(0, target_len - len(base))]
    return list(dict.fromkeys(result))


def _mutate(
    candidate: list[str],
    ingredient_names: list[str],
    min_ingredients: int,
    max_ingredients: int,
    must_include: list[str],
    excluded: set[str],
) -> list[str]:
    allowed = [n for n in ingredient_names if n not in excluded]
    if not allowed:
        return candidate

    result = list(candidate)
    mutation_type = random.choice(["add", "remove", "replace"])

    if mutation_type == "add" and len(result) < max_ingredients:
        choices = [n for n in allowed if n not in result]
        if choices:
            result.append(random.choice(choices))
    elif mutation_type == "remove" and len(result) > min_ingredients:
        removable = [n for n in result if n not in must_include]
        if removable:
            result.remove(random.choice(removable))
    else:
        if result:
            idx = random.randrange(len(result))
            choices = [n for n in allowed if n not in result]
            if choices and result[idx] not in must_include:
                result[idx] = random.choice(choices)

    for req in must_include:
        if req in allowed and req not in result:
            result.append(req)

    while len(result) > max_ingredients:
        removable = [n for n in result if n not in must_include]
        if not removable:
            break
        result.remove(random.choice(removable))

    return list(dict.fromkeys(result))


def _enforce_constraints(
    candidate: list[str],
    ingredient_names: list[str],
    min_ingredients: int,
    max_ingredients: int,
    must_include: list[str],
    excluded: set[str],
) -> list[str]:
    allowed = [n for n in ingredient_names if n not in excluded]
    allowed_set = set(allowed)
    if not allowed:
        return []

    # Keep only allowed ingredients.
    result = [x for x in candidate if x in allowed_set]

    # Force required ingredients.
    for req in must_include:
        if req in allowed_set and req not in result:
            result.append(req)

    # Grow to min size if needed.
    if len(result) < min_ingredients:
        pool = [x for x in allowed if x not in result]
        random.shuffle(pool)
        need = min_ingredients - len(result)
        result.extend(pool[:need])

    # Trim to max size, but never drop required ingredients.
    while len(result) > max_ingredients:
        removable = [x for x in result if x not in must_include]
        if not removable:
            break
        result.remove(random.choice(removable))

    return list(dict.fromkeys(result))


def _crossover(parent_a: list[str], parent_b: list[str], min_ingredients: int, max_ingredients: int) -> list[str]:
    union = list(dict.fromkeys(parent_a + parent_b))
    random.shuffle(union)
    target_len = random.randint(min_ingredients, max(min_ingredients, min(max_ingredients, len(union))))
    return union[:target_len]


def _build_tech_card(
    ingredients: list[str],
    ingredient_costs: dict[str, float],
    ingredient_grams_per_unit: dict[str, float],
    ingredient_units: dict[str, str],
    total_weight_grams: float,
) -> list[dict[str, Any]]:
    if not ingredients:
        return []
    base_gram = total_weight_grams / len(ingredients)
    rows: list[dict[str, Any]] = []
    for ingredient in ingredients:
        qty = round(base_gram, 1)
        unit_cost = round(ingredient_costs.get(ingredient, 10.0), 2)
        grams_in_unit = max(1.0, ingredient_grams_per_unit.get(ingredient, 1000.0))
        total = round(unit_cost * (qty / grams_in_unit), 2)
        rows.append(
            {
                "ingredientName": ingredient,
                "quantityGrams": qty,
                "unitCost": unit_cost,
                "unit": ingredient_units.get(ingredient, "kg"),
                "totalCost": total,
            }
        )
    return rows


def generate_new_dish(
    sales_records: list[dict],
    menu_items: list[dict],
    ingredients: list[dict],
    constraints: dict[str, Any] | None = None,
) -> dict[str, Any]:
    constraints = constraints or {}

    min_ingredients = max(2, _safe_int(constraints.get("minIngredients"), 3))
    max_ingredients = max(min_ingredients, _safe_int(constraints.get("maxIngredients"), 6))
    population_size = max(20, _safe_int(constraints.get("populationSize"), 80))
    generations = max(5, _safe_int(constraints.get("generations"), 40))

    must_include = _normalize_ingredients(constraints.get("mustInclude") or [])
    excluded = set(_normalize_ingredients(constraints.get("excludedIngredients") or []))

    ingredient_names, ingredient_costs, ingredient_grams_per_unit, ingredient_units = _ingredient_pool(ingredients)
    if not ingredient_names:
        return {"status": "failed", "errorMessage": "Нет данных по ингредиентам"}

    sales_weights = _build_sales_weights(sales_records)
    existing_dishes = _existing_dish_sets(menu_items)

    # Hard protection against generating (too) existing dishes.
    protection = constraints.get("existingDishProtection")
    if isinstance(protection, dict):
        protect_enabled = bool(protection.get("enabled", True))
        max_allowed_similarity = _safe_float(protection.get("maxSimilarity"), 0.9)
    else:
        # Default: enabled with fairly strict threshold.
        protect_enabled = True
        max_allowed_similarity = _safe_float(constraints.get("maxExistingSimilarity"), 0.9)
    if max_allowed_similarity <= 0:
        protect_enabled = False

    markup = _safe_float(constraints.get("markup"), 2.35)
    if markup < 1.3:
        markup = 1.3
    total_weight_grams = _safe_float(constraints.get("totalWeightGrams"), DEFAULT_TOTAL_WEIGHT_GRAMS)
    if total_weight_grams <= 0:
        total_weight_grams = DEFAULT_TOTAL_WEIGHT_GRAMS

    population: list[list[str]] = []
    attempts = 0
    max_attempts = population_size * 50
    while len(population) < population_size and attempts < max_attempts:
        attempts += 1
        c = _make_random_candidate(
            ingredient_names,
            min_ingredients,
            max_ingredients,
            must_include,
            excluded,
        )
        if not c:
            continue
        c = _enforce_constraints(c, ingredient_names, min_ingredients, max_ingredients, must_include, excluded)
        if not c:
            continue
        if protect_enabled and existing_dishes:
            if _is_too_similar(set(c), existing_dishes, max_allowed_similarity):
                continue
        population.append(c)

    if len(population) < max(2, population_size // 4):
        return {
            "status": "failed",
            "errorMessage": "Не удалось сформировать популяцию: слишком строгая защита от существующих блюд или мало ингредиентов",
        }

    best: Candidate | None = None
    history: list[float] = []

    for generation_idx in range(generations):
        scored = [
            _evaluate_candidate(
                _enforce_constraints(
                    candidate,
                    ingredient_names,
                    min_ingredients,
                    max_ingredients,
                    must_include,
                    excluded,
                ),
                ingredient_costs,
                ingredient_grams_per_unit,
                sales_weights,
                existing_dishes,
                markup,
                generation_idx + 1,
                total_weight_grams,
            )
            for candidate in population
        ]
        scored.sort(key=lambda x: x.fitness, reverse=True)
        current_best = scored[0]
        history.append(current_best.fitness)
        if best is None or current_best.fitness > best.fitness:
            best = current_best

        elite_count = max(2, population_size // 10)
        next_population = [s.ingredients for s in scored[:elite_count]]

        child_attempts = 0
        child_max_attempts = population_size * 50
        while len(next_population) < population_size and child_attempts < child_max_attempts:
            child_attempts += 1
            parent_a = random.choice(scored[: max(5, population_size // 3)]).ingredients
            parent_b = random.choice(scored[: max(5, population_size // 3)]).ingredients
            child = _crossover(parent_a, parent_b, min_ingredients, max_ingredients)
            if random.random() < 0.35:
                child = _mutate(
                    child,
                    ingredient_names,
                    min_ingredients,
                    max_ingredients,
                    must_include,
                    excluded,
                )
            child = _enforce_constraints(
                child,
                ingredient_names,
                min_ingredients,
                max_ingredients,
                must_include,
                excluded,
            )
            if not child:
                continue

            if protect_enabled and existing_dishes:
                # Repair loop: try a few mutations to escape similarity
                repair_tries = 0
                while _is_too_similar(set(child), existing_dishes, max_allowed_similarity) and repair_tries < 6:
                    repair_tries += 1
                    child = _mutate(
                        child,
                        ingredient_names,
                        min_ingredients,
                        max_ingredients,
                        must_include,
                        excluded,
                    )
                    child = _enforce_constraints(
                        child,
                        ingredient_names,
                        min_ingredients,
                        max_ingredients,
                        must_include,
                        excluded,
                    )
                    if not child:
                        break
                if not child:
                    continue
                if _is_too_similar(set(child), existing_dishes, max_allowed_similarity):
                    continue

            next_population.append(child)
        population = next_population[:population_size]

    if best is None:
        return {"status": "failed", "errorMessage": "Не удалось сгенерировать блюдо"}

    keyword = best.ingredients[0].capitalize() if best.ingredients else "Chef"
    dish_name = f"Авторский ролл {keyword}"

    reasoning = [
        "Комбинация содержит ингредиенты с высоким спросом по истории продаж",
        "Итоговая себестоимость удерживается в рабочем диапазоне",
        "Набор отличается от текущих блюд меню и сохраняет новизну",
    ]

    return {
        "status": "completed",
        "dish": {
            "name": dish_name,
            "ingredients": best.ingredients,
            "estimatedCost": best.estimated_cost,
            "recommendedPrice": best.recommended_price,
            "predictedSales": best.predicted_sales,
            "estimatedProfit": best.estimated_profit,
            "noveltyScore": best.novelty_score,
            "fitnessScore": best.fitness,
            "generationFound": best.generation_found,
            "totalWeightGrams": total_weight_grams,
            "reasoning": reasoning,
            "techCard": _build_tech_card(
                best.ingredients,
                ingredient_costs,
                ingredient_grams_per_unit,
                ingredient_units,
                total_weight_grams,
            ),
        },
        "stats": {
            "populationSize": population_size,
            "generations": generations,
            "bestFitnessPerGeneration": history,
            "usedSalesRecords": len(sales_records or []),
            "usedMenuItems": len(menu_items or []),
            "usedIngredients": len(ingredient_names),
            "existingDishProtection": {
                "enabled": protect_enabled,
                "maxAllowedSimilarity": max_allowed_similarity,
                "existingDishesCount": len(existing_dishes),
            },
        },
    }


def optimize_rolls(
    constraints: dict[str, Any] | None,
    ingredients: list[dict] | None,
    menu_items: list[dict] | None,
    sales_records: list[dict] | None,
) -> dict[str, Any]:
    """
    Lightweight optimizer for roll compositions.
    Returns a shape compatible with the existing frontend (optimizedRolls) and partially with Java DTOs (results).
    """
    constraints = constraints or {}
    ingredient_names, ingredient_costs, ingredient_grams_per_unit, _units = _ingredient_pool(ingredients or [])
    if not ingredient_names:
        return {"status": "failed", "errorMessage": "Нет пула ингредиентов для оптимизации"}

    min_ingredients = max(2, _safe_int(constraints.get("minIngredients"), 3))
    max_ingredients = max(min_ingredients, _safe_int(constraints.get("maxIngredients"), 6))
    num_results = max(1, _safe_int(constraints.get("numResults"), 5))
    population_size = max(20, _safe_int(constraints.get("populationSize"), 80))
    generations = max(5, _safe_int(constraints.get("generations"), 40))

    must_include = _normalize_ingredients(constraints.get("mustInclude") or [])
    excluded = set(_normalize_ingredients(constraints.get("excludedIngredients") or constraints.get("exclude") or []))

    max_cost = _safe_float(constraints.get("maxCost"), 0.0)
    min_profit_margin = _safe_float(constraints.get("minProfitMargin"), 0.0)
    markup = _safe_float(constraints.get("markup"), 2.35)
    if markup < 1.3:
        markup = 1.3

    total_weight_grams = _safe_float(constraints.get("totalWeightGrams"), DEFAULT_TOTAL_WEIGHT_GRAMS)
    if total_weight_grams <= 0:
        total_weight_grams = DEFAULT_TOTAL_WEIGHT_GRAMS

    sales_weights = _build_sales_weights(sales_records or [])
    existing_dishes = _existing_dish_sets(menu_items or [])

    # Optional: use trained ML model (XGBoost) to estimate sales instead of heuristic.
    use_trained_model = bool(constraints.get("useTrainedModelSales", True))
    ml_predict = None
    if use_trained_model:
        try:
            # Local import to avoid import-time cycles.
            from app.services import service as ml_service  # type: ignore

            if getattr(ml_service, "model", None) is None or getattr(ml_service, "mlb", None) is None:
                # Try loading if not loaded yet.
                try:
                    ml_service.load_model()
                except Exception:
                    pass

            if getattr(ml_service, "model", None) is not None and getattr(ml_service, "mlb", None) is not None:
                ml_predict = ml_service.predict_single
        except Exception:
            ml_predict = None

    # Similarity protection (reuse same knobs as generator)
    protection = constraints.get("existingDishProtection")
    if isinstance(protection, dict):
        protect_enabled = bool(protection.get("enabled", True))
        max_allowed_similarity = _safe_float(protection.get("maxSimilarity"), 0.9)
    else:
        protect_enabled = True
        max_allowed_similarity = _safe_float(constraints.get("maxExistingSimilarity"), 0.9)
    if max_allowed_similarity <= 0:
        protect_enabled = False

    # Focus optimization around existing dishes (default: enabled).
    # This makes optimizer suggest "variants" of existing menu items instead of random combos.
    focus = constraints.get("existingDishFocus")
    if isinstance(focus, dict):
        focus_enabled = bool(focus.get("enabled", True))
        min_focus_similarity = _safe_float(focus.get("minSimilarity"), 0.35)
        max_focus_similarity = _safe_float(focus.get("maxSimilarity"), max_allowed_similarity if protect_enabled else 0.98)
        focus_weight = _safe_float(focus.get("weight"), 0.35)
    else:
        focus_enabled = True
        min_focus_similarity = _safe_float(constraints.get("minExistingSimilarity"), 0.35)
        max_focus_similarity = _safe_float(constraints.get("maxExistingSimilarityFocus"), max_allowed_similarity if protect_enabled else 0.98)
        focus_weight = _safe_float(constraints.get("existingSimilarityWeight"), 0.35)

    min_focus_similarity = max(0.0, min(1.0, min_focus_similarity))
    max_focus_similarity = max(0.0, min(1.0, max_focus_similarity))
    focus_weight = max(0.0, min(1.0, focus_weight))
    if not existing_dishes:
        focus_enabled = False

    def score(candidate_ing: list[str], generation_idx: int) -> Candidate | None:
        candidate_ing = _enforce_constraints(
            candidate_ing, ingredient_names, min_ingredients, max_ingredients, must_include, excluded
        )
        if not candidate_ing:
            return None
        if protect_enabled and existing_dishes and _is_too_similar(set(candidate_ing), existing_dishes, max_allowed_similarity):
            return None

        similarity = _max_similarity(set(candidate_ing), existing_dishes) if existing_dishes else 0.0
        if focus_enabled:
            # Enforce that candidates are "variants" of existing dishes.
            if similarity < min_focus_similarity or similarity > max_focus_similarity:
                return None

        c = _evaluate_candidate(
            candidate_ing,
            ingredient_costs,
            ingredient_grams_per_unit,
            sales_weights,
            existing_dishes,
            markup,
            generation_idx,
            total_weight_grams,
        )

        # Replace heuristic predicted sales with trained model prediction when available.
        if ml_predict is not None:
            try:
                ml_sales = float(ml_predict(c.ingredients, None))
                # Keep it non-negative and within a sane range.
                if ml_sales < 0:
                    ml_sales = 0.0
                margin = max(c.recommended_price - c.estimated_cost, 0.0)
                est_profit = round(ml_sales * margin, 2)
                # Recompute fitness using the same novelty weighting as in _evaluate_candidate
                fitness = round(est_profit * (0.6 + 0.4 * c.novelty_score), 4)
                c = Candidate(
                    ingredients=c.ingredients,
                    fitness=fitness,
                    predicted_sales=round(ml_sales, 2),
                    estimated_cost=c.estimated_cost,
                    recommended_price=c.recommended_price,
                    estimated_profit=est_profit,
                    novelty_score=c.novelty_score,
                    generation_found=c.generation_found,
                )
            except Exception:
                # If ML model isn't trained/compatible, silently keep heuristic.
                pass

        # Shift fitness from "novelty" to "similarity to existing menu" when focus is enabled.
        if focus_enabled:
            # Original: profit * (0.6 + 0.4 * novelty)
            # Focused: profit * (0.65 + focus_weight * similarity + (0.35 - focus_weight) * novelty)
            novelty = c.novelty_score
            focused_multiplier = 0.65 + focus_weight * similarity + max(0.0, (0.35 - focus_weight)) * novelty
            c = Candidate(
                ingredients=c.ingredients,
                fitness=round(c.estimated_profit * focused_multiplier, 4),
                predicted_sales=c.predicted_sales,
                estimated_cost=c.estimated_cost,
                recommended_price=c.recommended_price,
                estimated_profit=c.estimated_profit,
                novelty_score=c.novelty_score,
                generation_found=c.generation_found,
            )

        # Apply hard business constraints.
        if max_cost > 0 and c.estimated_cost > max_cost:
            return None
        if min_profit_margin > 0:
            margin = 0.0
            if c.recommended_price > 0:
                margin = max(c.recommended_price - c.estimated_cost, 0.0) / c.recommended_price
            if margin < min_profit_margin:
                return None
        return c

    # init population
    population: list[list[str]] = []
    # Seed population with existing menu items to keep focus on existing dishes.
    if focus_enabled and menu_items:
        for item in menu_items:
            if not isinstance(item, dict):
                continue
            seed = _normalize_ingredients(item.get("ingredients"))
            seed = _enforce_constraints(seed, ingredient_names, min_ingredients, max_ingredients, must_include, excluded)
            if seed:
                population.append(seed)

    # Ensure diversity: also add "nearby" variants of menu seeds.
    if focus_enabled and population:
        seeds_snapshot = population[: min(len(population), max(10, population_size // 2))]
        for s in seeds_snapshot:
            v = _mutate(s, ingredient_names, min_ingredients, max_ingredients, must_include, excluded)
            v = _enforce_constraints(v, ingredient_names, min_ingredients, max_ingredients, must_include, excluded)
            if v:
                population.append(v)

    attempts = 0
    while len(population) < population_size and attempts < population_size * 80:
        attempts += 1
        cand = _make_random_candidate(ingredient_names, min_ingredients, max_ingredients, must_include, excluded)
        if cand:
            population.append(cand)
    population = population[:population_size]
    if not population:
        return {"status": "failed", "errorMessage": "Не удалось сформировать стартовую популяцию"}

    best_candidates: list[Candidate] = []
    for gen in range(1, generations + 1):
        scored: list[Candidate] = []
        for cand in population:
            s = score(cand, gen)
            if s:
                scored.append(s)
        if not scored:
            # if constraints too strict, relax similarity protection a bit for this generation
            if protect_enabled and max_allowed_similarity < 0.99:
                max_allowed_similarity = min(0.99, max_allowed_similarity + 0.02)
                continue
            if focus_enabled and min_focus_similarity > 0.0:
                # relax focus if too strict
                min_focus_similarity = max(0.0, min_focus_similarity - 0.05)
                continue
            return {"status": "failed", "errorMessage": "Слишком строгие ограничения: не осталось допустимых вариантов"}

        scored.sort(key=lambda x: x.fitness, reverse=True)
        best_candidates = scored[: max(num_results * 10, 30)]

        elite = [c.ingredients for c in scored[: max(2, population_size // 10)]]
        next_population = list(elite)
        while len(next_population) < population_size:
            parent_a = random.choice(best_candidates).ingredients
            parent_b = random.choice(best_candidates).ingredients
            child = _crossover(parent_a, parent_b, min_ingredients, max_ingredients)
            if random.random() < 0.45:
                child = _mutate(child, ingredient_names, min_ingredients, max_ingredients, must_include, excluded)
            next_population.append(child)
        population = next_population

    # build response
    unique_by_set: dict[frozenset[str], Candidate] = {}
    for c in best_candidates:
        key = frozenset(c.ingredients)
        if key not in unique_by_set or c.fitness > unique_by_set[key].fitness:
            unique_by_set[key] = c
    final_sorted = sorted(unique_by_set.values(), key=lambda x: x.fitness, reverse=True)

    # Ensure we return exactly num_results if possible by generating extra diversified candidates.
    # Diversity rule: do not include two candidates with Jaccard similarity >= 0.9.
    final: list[Candidate] = []
    for c in final_sorted:
        s = set(c.ingredients)
        if any(_jaccard(s, set(x.ingredients)) >= 0.9 for x in final):
            continue
        final.append(c)
        if len(final) >= num_results:
            break

    # If still not enough, allow closer variants (relax diversity threshold) but keep uniqueness.
    if len(final) < num_results:
        for c in final_sorted:
            if c in final:
                continue
            final.append(c)
            if len(final) >= num_results:
                break

    # If still not enough results, actively generate more candidates by mutating the best ones.
    # We keep hard business constraints, but gradually relax "focus on existing dishes" and diversity.
    if len(final) < num_results:
        base_parents = final_sorted[: max(10, num_results * 10)] if final_sorted else []
        relax_steps = [
            # (min_focus_similarity_delta, diversity_threshold)
            (0.0, 0.95),
            (-0.05, 0.97),
            (-0.10, 0.99),
            (-0.20, 1.0),
        ]
        for delta, diversity_th in relax_steps:
            if len(final) >= num_results:
                break
            local_min_focus = min_focus_similarity + delta
            local_min_focus = max(0.0, min(1.0, local_min_focus))

            attempts = 0
            max_attempts = 800
            while len(final) < num_results and attempts < max_attempts:
                attempts += 1
                if base_parents:
                    parent = random.choice(base_parents).ingredients
                else:
                    parent = _make_random_candidate(
                        ingredient_names, min_ingredients, max_ingredients, must_include, excluded
                    )
                child = _mutate(parent, ingredient_names, min_ingredients, max_ingredients, must_include, excluded)
                s = score(child, generations + 1)
                if s is None:
                    continue

                if focus_enabled:
                    sim = _max_similarity(set(s.ingredients), existing_dishes) if existing_dishes else 0.0
                    if sim < local_min_focus:
                        continue

                if any(frozenset(s.ingredients) == frozenset(x.ingredients) for x in final):
                    continue
                if diversity_th < 1.0 and any(_jaccard(set(s.ingredients), set(x.ingredients)) >= diversity_th for x in final):
                    continue
                final.append(s)

    optimized_rolls = []
    for idx, c in enumerate(final, start=1):
        margin = 0.0
        if c.recommended_price > 0:
            margin = max(c.recommended_price - c.estimated_cost, 0.0) / c.recommended_price
        optimized_rolls.append(
            {
                "id": str(idx),
                "name": f"Оптимизированный ролл {idx}",
                "ingredients": c.ingredients,
                "predictedSales": c.predicted_sales,
                "confidenceScore": 0.85,
                "cost": c.estimated_cost,
                "estimatedCost": c.estimated_cost,
                "estimatedProfit": c.estimated_profit,
                "profitMargin": round(margin, 4),
                "noveltyScore": c.novelty_score,
                "score": round(min(1.0, c.fitness / max(1.0, final[0].fitness if final else 1.0)), 4),
                "fitnessScore": c.fitness,
                "generationFound": c.generation_found,
                "explanation": "Подобрано по максимуму прибыли с учетом новизны и ограничений",
            }
        )

    return {
        "status": "completed",
        # For current frontend
        "optimizedRolls": optimized_rolls,
        # For Java DTO compatibility
        "results": optimized_rolls,
        "statistics": {
            "populationSize": population_size,
            "generations": generations,
        },
    }
