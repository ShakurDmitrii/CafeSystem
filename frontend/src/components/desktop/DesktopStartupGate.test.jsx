import { render, screen, waitFor } from "@testing-library/react";
import { invoke, isTauri } from "@tauri-apps/api/core";
import DesktopStartupGate from "./DesktopStartupGate";

jest.mock("@tauri-apps/api/core", () => ({
    invoke: jest.fn(),
    isTauri: jest.fn()
}));

beforeEach(() => {
    jest.clearAllMocks();
});

test("starts local services before showing the desktop application", async () => {
    isTauri.mockReturnValue(true);
    invoke.mockResolvedValue({ success: true, message: "Сервисы готовы" });

    render(
        <DesktopStartupGate>
            <div>Экран входа</div>
        </DesktopStartupGate>
    );

    expect(screen.getByText(/запускаем cafehelp/i)).toBeInTheDocument();
    expect(screen.queryByText("Экран входа")).not.toBeInTheDocument();
    await waitFor(() => expect(invoke).toHaveBeenCalledWith("start_local_services"));
    expect(await screen.findByText("Экран входа")).toBeInTheDocument();
});

test("does not manage Docker when opened in a regular browser", () => {
    isTauri.mockReturnValue(false);

    render(
        <DesktopStartupGate>
            <div>Экран входа</div>
        </DesktopStartupGate>
    );

    expect(screen.getByText("Экран входа")).toBeInTheDocument();
    expect(invoke).not.toHaveBeenCalled();
});
