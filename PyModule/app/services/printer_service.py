# from app.printers.generate_consignment_docx import WindowsPrinter
# from app.printers.escpos_printer import EscposPrinter
# from app.templates.consignment_ticket import build_ticket
# from app.services.analytics_service import log_print
# from app.config import settings
#
#
# def print_consignment(data):
#     lines = build_ticket(data)
#
#     if settings.DEFAULT_PRINTER == "escpos":
#         printer = EscposPrinter()
#     else:
#         printer = WindowsPrinter()
#
#     printer.print_lines(lines)
#     log_print(data.consignmentId)
