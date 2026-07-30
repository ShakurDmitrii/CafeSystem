import unittest
from datetime import datetime, timedelta

from app.services.analytics import (
    analyze_top_rolls,
    calculate_kpi,
    generate_sales_trend,
    process_sales_data,
)


class AnalyticsServiceTests(unittest.TestCase):
    def setUp(self) -> None:
        today = datetime.now().date()
        self.records = [
            {
                "rollName": "Филадельфия",
                "saleDate": today.isoformat(),
                "quantity": 2,
                "pricePerUnit": 100,
                "totalAmount": 200,
                "unitCost": 60,
            },
            {
                "rollName": "Старый ролл",
                "saleDate": (today - timedelta(days=40)).isoformat(),
                "quantity": 10,
                "pricePerUnit": 50,
                "totalAmount": 500,
                "unitCost": 25,
            },
        ]

    def test_profit_uses_real_unit_cost(self) -> None:
        frame = process_sales_data(self.records)

        current = frame.loc[frame["rollName"] == "Филадельфия"].iloc[0]
        self.assertEqual(current["cost"], 120.0)
        self.assertEqual(current["profit"], 80.0)
        self.assertEqual(current["margin"], 40.0)

    def test_kpi_is_deterministic_and_filters_selected_period(self) -> None:
        frame = process_sales_data(self.records)

        first = calculate_kpi(frame, "week")
        second = calculate_kpi(frame, "week")

        self.assertEqual(first, second)
        self.assertEqual(first["total_profit"], 80.0)
        self.assertEqual(first["total_sales"], 2)
        self.assertIsNone(first["model_accuracy"])

    def test_top_rolls_and_trend_use_selected_period(self) -> None:
        frame = process_sales_data(self.records)

        top = analyze_top_rolls(frame, "week")
        trend = generate_sales_trend(frame, "week")

        self.assertEqual([item["name"] for item in top], ["Филадельфия"])
        self.assertEqual(len(trend), 1)
        self.assertEqual(trend[0]["revenue"], 200.0)
        self.assertIsNone(trend[0]["predicted"])


if __name__ == "__main__":
    unittest.main()
