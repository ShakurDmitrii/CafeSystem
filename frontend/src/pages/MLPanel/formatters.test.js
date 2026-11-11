import { describeTrainingResult } from './formatters';

const plain = (text) => String(text).replace(/\s/g, ' ');

test('training summary shows the period and restored zero days', () => {
    expect(plain(describeTrainingResult({
        recordsCount: 1250,
        newIngredientsCount: 18,
        trainingResult: {
            trainingStartDate: '2025-01-01',
            trainingEndDate: '2026-10-03',
            zeroSaleDaysAdded: 210,
            warnings: ['Новый состав не проверен.']
        }
    }))).toBe('Модель обучена на продажах с 01.01.2025 по 03.10.2026: 1 250 записей по блюдам и дням, '
        + 'из них дней без продаж: 210, 18 ингредиентов. Новый состав не проверен.');
});

test('trimmed history is reported', () => {
    expect(describeTrainingResult({ recordsCount: 100000, newIngredientsCount: 40, trimmedToRecent: true, trainingResult: {} }))
        .toContain('взяты самые свежие продажи');
});
