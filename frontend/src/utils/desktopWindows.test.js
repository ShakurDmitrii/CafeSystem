import { isTauri } from "@tauri-apps/api/core";
import { openAppWindow } from "./desktopWindows";

jest.mock("@tauri-apps/api/core", () => ({
    isTauri: jest.fn()
}));

test("uses the browser popup outside Tauri", async () => {
    isTauri.mockReturnValue(false);
    const popup = { focus: jest.fn() };
    const open = jest.spyOn(window, "open").mockReturnValue(popup);

    await openAppWindow({
        label: "kitchen-53",
        path: "/kitchen-display/53",
        title: "Экран кухни"
    });

    expect(open).toHaveBeenCalledWith(
        `${window.location.origin}/kitchen-display/53`,
        "_blank",
        expect.any(String)
    );
    open.mockRestore();
});
