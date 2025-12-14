class EscposPrinter:
    def print_lines(self, lines):
        # Пока просто лог, потом подключишь python-escpos
        print("=== ESC/POS PRINT ===")
        for line in lines:
            print(line)
        print("=== END ===")
