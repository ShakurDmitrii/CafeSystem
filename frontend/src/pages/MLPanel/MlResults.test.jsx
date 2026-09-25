import React from 'react';
import { render, screen } from '@testing-library/react';
import PredictionResult from './PredictionResult';
import { formatCurrency, formatNumber, formatPercent } from './formatters';

test('unknown metrics are not displayed as zero', () => {
    expect(formatCurrency(null)).toBe('—');
    expect(formatNumber('')).toBe('—');
    expect(formatPercent(null)).toBe('—');
    expect(formatNumber(0)).toBe('0');
});

test('prediction does not fabricate confidence or hide its limitations', () => {
    render(<PredictionResult prediction={{ predictedSales: 3, confidenceScore: null,
        target: 'daily_quantity_on_sale_days', warnings: ['Нет истории доступности'], modelVersion: 'test' }} />);
    expect(screen.queryByText(/Уверенность 0%/)).not.toBeInTheDocument();
    expect(screen.getByText('Уверенность не оценена')).toBeInTheDocument();
    expect(screen.getByText('Нет истории доступности')).toBeInTheDocument();
    expect(screen.getByText('порций в день с продажами')).toBeInTheDocument();
});
