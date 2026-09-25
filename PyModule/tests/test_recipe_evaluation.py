import random
import unittest
from unittest.mock import patch

from app.services import dish_generator as generator
from app.services.recipe_evaluation import RecipeEvaluation


class RecipeEvaluationTests(unittest.TestCase):
    def setUp(self):
        random.seed(9)
        self.ingredients = [dict(name=name, unit="g", costPerUnit=cost, costSource="warehouse_average")
                            for name, cost in [("rice", 0.1), ("fish", 1), ("sauce", 0.5), ("cucumber", 0.2)]]
        self.menu = [dict(price=300, ingredients=["rice", "fish", "sauce"],
                          ingredientQuantities=dict(rice=100, fish=30, sauce=10, cucumber=20))]
        self.constraints = dict(minIngredients=2, maxIngredients=3, populationSize=20, generations=5,
                                useTrainedModelSales=False, existingDishProtection=dict(enabled=False),
                                existingDishFocus=dict(enabled=False))

    def evaluate(self, costs):
        context = RecipeEvaluation(self.menu, self.ingredients, self.constraints)
        return generator._evaluate_candidate(["rice", "fish", "sauce"], costs,
                                            dict(rice=1, fish=1, sauce=1), {}, [], 2.35, 1, 140, context)

    def test_expensive_ingredients_do_not_increase_fitness(self):
        cheap = self.evaluate(dict(rice=0.1, fish=1, sauce=0.5))
        costly = self.evaluate(dict(rice=0.2, fish=2, sauce=1))
        self.assertGreater(cheap.fitness, costly.fitness)
        self.assertEqual(cheap.recommended_price, costly.recommended_price)

    def test_grammage_and_cost_use_same_recipe(self):
        candidate = self.evaluate(dict(rice=0.1, fish=1, sauce=0.5))
        self.assertEqual([row["quantityGrams"] for row in candidate.tech_card], [100, 30, 10])
        self.assertEqual(candidate.estimated_cost, 45)
        self.assertIsNone(candidate.predicted_sales)
        self.assertIsNone(candidate.estimated_profit)

    def test_rounding_preserves_total_weight(self):
        context = RecipeEvaluation(self.menu, self.ingredients, self.constraints)
        self.assertAlmostEqual(sum(context.quantities(["rice", "fish", "sauce"], 139.9).values()), 139.9)

    def test_generator_and_optimizer_label_heuristic_and_null_confidence(self):
        generated = generator.generate_new_dish([], self.menu, self.ingredients, self.constraints)
        optimized = generator.optimize_rolls(self.constraints, self.ingredients, self.menu, [])
        self.assertEqual(generated["status"], "completed")
        self.assertEqual(optimized["status"], "completed")
        for candidate in [generated["dish"], *optimized["results"]]:
            self.assertEqual(candidate["salesSource"], "heuristic")
            self.assertIsNone(candidate["predictedSales"])
            self.assertIsNone(candidate["confidenceScore"])
            self.assertTrue(candidate["warnings"])
            self.assertAlmostEqual(candidate["estimatedCost"], sum(row["totalCost"] for row in candidate["techCard"]))

    def test_missing_required_ingredient_is_not_silently_dropped(self):
        constraints = dict(self.constraints, mustInclude=["missing"])
        with self.assertRaisesRegex(ValueError, "Обязательные"):
            generator.generate_new_dish([], self.menu, self.ingredients, constraints)

    def test_missing_quantities_do_not_become_equal_parts(self):
        with self.assertRaisesRegex(ValueError, "граммовок"):
            generator.generate_new_dish([], [], self.ingredients, self.constraints)

    def test_no_silent_relaxation_of_similarity_protection(self):
        constraints = dict(self.constraints, minIngredients=3, maxIngredients=3,
                           existingDishProtection=dict(enabled=True, maxSimilarity=0.2))
        result = generator.optimize_rolls(constraints, self.ingredients, self.menu, [])
        self.assertEqual(result["status"], "failed")

    def test_loaded_model_failure_is_not_silently_turned_into_heuristic(self):
        class Binarizer:
            classes_ = ["rice", "fish", "sauce", "cucumber"]
        class Model:
            def predict(self, features):
                raise RuntimeError("broken model")
        constraints = dict(self.constraints, useTrainedModelSales=True)
        with patch("app.services.service._snapshot_state", return_value=(Model(), Binarizer(),
                   {"target": "daily_quantity_on_sale_days"})), patch("app.services.service._build_features", return_value=[]):
            with self.assertRaisesRegex(RuntimeError, "broken model"):
                generator.generate_new_dish([], self.menu, self.ingredients, constraints)
