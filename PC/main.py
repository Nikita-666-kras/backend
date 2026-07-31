import json
import os
import shutil
from datetime import datetime
import tkinter as tk
from tkinter import ttk, messagebox
from pdfrw import PdfReader, PdfWriter, PdfDict
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import letter
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from io import BytesIO
from io import BytesIO
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import letter
from reportlab.pdfbase.pdfmetrics import stringWidth
from pdfrw import PdfReader
from PyPDF2 import PdfReader as PyPdfReader, PdfWriter as PyPdfWriter
import sys

# === Пути к ресурсам и пользовательским данным ===
if getattr(sys, 'frozen', False):
    RESOURCE_PATH = sys._MEIPASS
    APP_PATH = os.path.dirname(sys.executable)
else:
    RESOURCE_PATH = os.path.dirname(os.path.abspath(__file__))
    APP_PATH = RESOURCE_PATH

# === Папка AppData ===
appdata_path = os.path.join(os.getenv("LOCALAPPDATA"), "AgroTechKP")
os.makedirs(appdata_path, exist_ok=True)

user_price_path = os.path.join(appdata_path, "price_list.json")
default_price_path = os.path.join(RESOURCE_PATH, "price_list.json")


# === Настройка шрифтов ===
pdfmetrics.registerFont(TTFont('Arial', 'arial.ttf'))  # убедись, что arial.ttf лежит рядом или в системе

# === Проверка версии и обновление JSON ===

with open(default_price_path, "r", encoding="utf-8") as f:
    default_prices = json.load(f)

if os.path.exists(user_price_path):

    with open(user_price_path, "r", encoding="utf-8") as f:
        user_prices = json.load(f)

    if user_prices.get("config_version", 0) < default_prices.get("config_version", 0):

        # сохраняем номер КП
        last_kp = user_prices.get("last_kp_number", 0)

        shutil.copy(default_price_path, user_price_path)

        with open(user_price_path, "r+", encoding="utf-8") as f:
            updated = json.load(f)
            updated["last_kp_number"] = last_kp
            f.seek(0)
            json.dump(updated, f, ensure_ascii=False, indent=2)
            f.truncate()

else:
    shutil.copy(default_price_path, user_price_path)

price_path = user_price_path

with open(price_path, "r", encoding="utf-8") as f:
    PRICES = json.load(f)


def generate_kp():
    model = model_var.get()
    for_who = for_who_var.get()
    count = int(count_var.get())
    target_price = float(target_price_var.get())

    # === Получение и увеличение номера КП ===
    kp_number = PRICES.get("last_kp_number", 522) + 1
    PRICES["last_kp_number"] = kp_number
    # сохраняем обратно в price_list.json
    with open(price_path, "w", encoding="utf-8") as f:
        json.dump(PRICES, f, ensure_ascii=False, indent=2)

    # === Используем номер КП для имени файла и вставки в PDF ===
    name_pdf = f"КП №{kp_number} от {datetime.now().strftime('%d.%m.%y')} ({model})"

    data = PRICES[model]
    start_price = data["start_price"]
    diff = start_price - target_price

    # --- пересчёт ---
    drone_price = data.get("drone_price", 0) - diff
    akb_price = data.get("akb_price", 0)
    zaryad_price = data.get("zaryad_price", 0)
    WB37_hub_price = data.get("WB37_hub_price", 0)
    WB37_price = data.get("WB37_price", 0)

    ct = count
    ct_2 = ct * 2
    ct_3 = ct * 3

    drone_price_total = drone_price * ct
    akb_price_total = akb_price * ct_3
    zaryad_price_total = zaryad_price * ct
    WB37_hub_price_total = WB37_hub_price * ct
    WB37_price_total = WB37_price * ct_2

    price_target_total = target_price * ct
    vat_mode = data.get("vat_mode", "mixed")

    if vat_mode == "mixed":
        nds_base = price_target_total - drone_price_total
    else:
        nds_base = price_target_total

    nds_total = round(nds_base * 22 / 122, 2)

    # --- Формируем словарь для подстановки в PDF ---
    fields = {
        "name_pdf": name_pdf,
        "for_who": for_who,
        "ct": str(ct),
        "ct_2": str(ct_2),
        "ct_3": str(ct_3),
        "drone_price": f"{drone_price:,.0f}".replace(",", " "),
        "akb_price": f"{akb_price:,.0f}".replace(",", " "),
        "zaryad_price": f"{zaryad_price:,.0f}".replace(",", " "),
        "WB37_hub_price": f"{WB37_hub_price:,.0f}".replace(",", " "),
        "WB37_price": f"{WB37_price:,.0f}".replace(",", " "),
        "drone_price_total": f"{drone_price_total:,.0f}".replace(",", " "),
        "akb_price_total": f"{akb_price_total:,.0f}".replace(",", " "),
        "zaryad_price_total": f"{zaryad_price_total:,.0f}".replace(",", " "),
        "WB37_hub_price_total": f"{WB37_hub_price_total:,.0f}".replace(",", " "),
        "WB37_price_total": f"{WB37_price_total:,.0f}".replace(",", " "),
        "price_target": f"{target_price:,.0f}".replace(",", " "),
        "price_target_total": f"{price_target_total:,.0f}".replace(",", " "),
        "nds_total": f"{nds_total:,.2f}".replace(",", " "),
    # дубли для новых форм
        "1ct": str(ct),
        "2ct": str(ct),
        "3ct": str(ct),
        "1price_target_total": f"{price_target_total:,.0f}".replace(",", " ") + " .₽ ",
        "2price_target_total": f"{price_target_total:,.0f}".replace(",", " ") + " рублей, включая НДС.",
        "1nds_total": f"{nds_total:,.2f}".replace(",", " ") + " .₽ ",
        "2nds_total": "Общая сумма к вычету: " + f"{nds_total:,.2f}".replace(",", " ") + " рубля",
        }


    # === Работа с PDF ===
        # Путь к шаблону PDF
    template_path = os.path.join(RESOURCE_PATH, f"{model.replace(' ', '_')}.pdf")


    # Фиксированная директория для сохранения
    output_dir = os.path.join(APP_PATH, "Архив кп (с калькулятора)")
    os.makedirs(output_dir, exist_ok=True)


    # Имя файла для сохранения с дополнительным текстом
    file_name = f"{fields['name_pdf']} - Агродрон с НДС от АГРО ТЕХНОЛОГИИ.pdf"
    # Полный путь к файлу
    output_path = os.path.join(output_dir, file_name)


    # Список полей, которые нужно выравнивать по центру
    center_fields = [
        "drone_price", "akb_price", "zaryad_price",
        "WB37_hub_price", "WB37_price",
        "drone_price_total", "akb_price_total", "zaryad_price_total",
        "WB37_hub_price_total", "WB37_price_total"
    ]

    # Создаем PDF-слой для текста
    packet = BytesIO()
    can = canvas.Canvas(packet, pagesize=letter)

    pdf_template = PdfReader(template_path)

    for page_num, page in enumerate(pdf_template.pages):
        if page.Annots:
            for annot in page.Annots:
                if annot.T and annot.Rect:
                    key = annot.T.to_unicode()
                    if key in fields:
                        x0, y0, x1, y1 = [float(v) for v in annot.Rect]
                        width = x1 - x0
                        height = y1 - y0
                        font_size = min(height * 0.7, 14)
                        can.setFont("Arial", font_size)
                        text = str(fields[key])
                        if key in center_fields:
                            # центрирование
                            text_width = stringWidth(text, "Arial", font_size)
                            x_centered = x0 + (width - text_width) / 2
                            can.drawString(x_centered, y0 + height * 0.15, text)
                        else:
                            # слева
                            can.drawString(x0, y0 + height * 0.15, text)
        can.showPage()  # переходим на следующую страницу

    # Сохраняем слой текста один раз
    can.save()
    packet.seek(0)

    # Объединяем слой с оригинальным PDF
    existing_pdf = PyPdfReader(template_path)
    overlay_pdf = PyPdfReader(packet)
    output_pdf = PyPdfWriter()

    for i in range(len(existing_pdf.pages)):
        page = existing_pdf.pages[i]
        overlay = overlay_pdf.pages[i]
        page.merge_page(overlay)
        # удаляем все формы
        if "/Annots" in page:
            del page["/Annots"]
        output_pdf.add_page(page)

    with open(output_path, "wb") as f_out:
        output_pdf.write(f_out)


    messagebox.showinfo("Готово", f"КП успешно создано:\n{output_path}")

# === GUI ===
root = tk.Tk()
root.title("Генератор КП Agro-Tech")
root.geometry("400x450")
root.resizable(False, False)

tk.Label(root, text="Модель дрона:").pack(pady=5)
model_var = tk.StringVar(value="DJI T50")
# Формируем список моделей без служебного ключа "last_kp_number"
drone_models = [key for key in PRICES.keys() if key != "last_kp_number"]
model_menu = ttk.Combobox(root, textvariable=model_var, values=drone_models, state="readonly")
model_menu.pack()

def update_start_price(event=None):
    selected_model = model_var.get()
    start_price = PRICES[selected_model]["start_price"]
    target_price_var.set(str(start_price))

model_menu.bind("<<ComboboxSelected>>", update_start_price)


tk.Label(root, text="Для кого:").pack(pady=5)
for_who_var = tk.StringVar(value="УВАЖАЕМЫЕ КОЛЛЕГИ")
tk.Entry(root, textvariable=for_who_var, width=40).pack()

tk.Label(root, text="Количество комплектов:").pack(pady=5)
count_var = tk.StringVar(value="1")
tk.Entry(root, textvariable=count_var, width=10).pack()

tk.Label(root, text="Цена одного комплекта:").pack(pady=5)
target_price_var = tk.StringVar(value=str(PRICES["DJI T50"]["start_price"]))
tk.Entry(root, textvariable=target_price_var, width=15).pack()

tk.Button(root, text="Сгенерировать КП", command=generate_kp, bg="#3c9", fg="white").pack(pady=20)

root.mainloop()
