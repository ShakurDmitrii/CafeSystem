import csv
from datetime import date, timedelta
import random


def generate_test_data_csv(filename='ml_training_data.csv', num_records=1000):
    roll_names = ['Ролл Калифорния', 'Филадельфия', 'Рис с лососем', 'Веган ролл', 'Дракон']
    ingredients_options = [
        ['рис', 'лосось', 'авокадо'],
        ['рис', 'угорь', 'огурец'],
        ['рис', 'лосось', 'сыр'],
        ['рис', 'авокадо', 'морковь'],
        ['рис', 'креветка', 'огурец']
    ]

    start_date = date.today() - timedelta(days=90)

    with open(filename, mode='w', newline='', encoding='utf-8') as csv_file:
        fieldnames = ['rollName', 'ingredients', 'sales', 'date', 'dayOfWeek', 'month', 'isActive']
        writer = csv.DictWriter(csv_file, fieldnames=fieldnames)
        writer.writeheader()

        for _ in range(num_records):
            roll_index = random.randint(0, len(roll_names) - 1)
            roll = roll_names[roll_index]
            ingredients = ingredients_options[roll_index]
            sale_date = start_date + timedelta(days=random.randint(0, 90))
            sales = random.randint(10, 150)
            is_active = random.choice([True, True, True, False])

            writer.writerow({
                'rollName': roll,
                'ingredients': '|'.join(ingredients),  # разделитель для CSV
                'sales': sales,
                'date': sale_date.isoformat(),
                'dayOfWeek': sale_date.isoweekday(),
                'month': sale_date.month,
                'isActive': is_active
            })

    print(f"CSV файл '{filename}' с {num_records} записями создан.")


# Генерация файла
generate_test_data_csv('ml_training_data.csv', 1000)
