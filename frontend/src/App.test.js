import { render, screen } from '@testing-library/react';
import App from './App';

jest.mock('@tauri-apps/api/core', () => ({
  invoke: jest.fn(),
  isTauri: jest.fn(() => false)
}));

test('renders the employee login screen for an anonymous user', () => {
  window.localStorage.clear();
  render(<App />);
  expect(
    screen.getByRole('heading', { name: /войдите в рабочее пространство/i })
  ).toBeInTheDocument();
});
