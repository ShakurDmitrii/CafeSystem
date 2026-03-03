import random
from dataclasses import dataclass
from typing import Any


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


def _ingredient_pool(ingredients: list[dict]) -> tuple[list[str], dict[str, float]]:
    names: list[str] = []
    costs: dict[str, float] = {}
    for item in ingredients or []:
        if not isinstance(item, dict):
            continue
        name = str(item.get("name", "")).strip().lower()
        if not name:
            continue
        cost = _safe_float(item.get("costPerUnit"), 0.0)
        if cost <= 0:
            cost = 10.0
        names.append(name)
        costs[name] = cost
    unique_names = list(dict.fromkeys(names))
    return unique_names, costs


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


def _evaluate_candidate(
    ingredients: list[str],
    ingredient_costs: dict[str, float],
    sales_weights: dict[str, float],
    existing_dishes: list[set[str]],
    markup: float,
    generation_idx: int,
) -> Candidate:
    unique_ingredients = list(dict.fromkeys(ingredients))
    current_set = set(unique_ingredients)

    estimated_cost = sum(ingredient_costs.get(ing, 10.0) for ing in unique_ingredients)
    recommended_price = round(max(estimated_cost * markup, estimated_cost + 20.0), 2)
    margin = max(recommended_price - estimated_cost, 0.0)

    popularity = sum(sales_weights.get(ing, 0.0) for ing in unique_ingredients)
    predicted_sales = round(8.0 + popularity / 10.0, 2)

    if existing_dishes:
        max_similarity = max(_jaccard(current_set, dish) for dish in existing_dishes)
    else:
        max_similarity = 0.0
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


def _crossover(parent_a: list[str], parent_b: list[str], min_ingredients: int, max_ingredients: int) -> list[str]:
    union = list(dict.fromkeys(parent_a + parent_b))
    random.shuffle(union)
    target_len = random.randint(min_ingredients, max(min_ingredients, min(max_ingredients, len(union))))
    return union[:target_len]


def _build_tech_card(ingredients: list[str], ingredient_costs: dict[str, float]) -> list[dict[str, Any]]:
    if not ingredients:
        return []
    base_gram = 140.0 / len(ingredients)
    rows: list[dict[str, Any]] = []
    for ingredient in ingredients:
        qty = round(base_gram, 1)
        unit_cost = round(ingredient_costs.get(ingredient, 10.0), 2)
        total = round(unit_cost * (qty / 100.0), 2)
        rows.append(
            {
                "ingredientName": ingredient,
                "quantityGrams": qty,
                "unitCost": unit_cost,
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

    ingredient_names, ingredient_costs = _ingredient_pool(ingredients)
    if not ingredient_names:
        return {"status": "failed", "errorMessage": "Нет данных по ингредиентам"}

    sales_weights = _build_sales_weights(sales_records)
    existing_dishes = _existing_dish_sets(menu_items)

    markup = _safe_float(constraints.get("markup"), 2.35)
    if markup < 1.3:
        markup = 1.3

    population: list[list[str]] = []
    while len(population) < population_size:
        c = _make_random_candidate(
            ingredient_names,
            min_ingredients,
            max_ingredients,
            must_include,
            excluded,
        )
        if c:
            population.append(c)

    best: Candidate | None = None
    history: list[float] = []

    for generation_idx in range(generations):
        scored = [
            _evaluate_candidate(
                candidate,
                ingredient_costs,
                sales_weights,
                existing_dishes,
                markup,
                generation_idx + 1,
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

        while len(next_population) < population_size:
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
            if child:
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
            "reasoning": reasoning,
            "techCard": _build_tech_card(best.ingredients, ingredient_costs),
        },
        "stats": {
            "populationSize": population_size,
            "generations": generations,
            "bestFitnessPerGeneration": history,
            "usedSalesRecords": len(sales_records or []),
            "usedMenuItems": len(menu_items or []),
            "usedIngredients": len(ingredient_names),
        },
    }
