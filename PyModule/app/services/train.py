from app.services.TestTrainingService import TestTrainingService


test_service = TestTrainingService()

# Обучение на тестовых данных
result = test_service.train_on_test_data(n_records=5000)
print(result)

# Предсказание
prediction = test_service.predict(["рис", "лосось"])
print("Predicted sales:", prediction)
