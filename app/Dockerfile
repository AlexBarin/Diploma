FROM python:3.12-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

COPY requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

COPY main.py /app/main.py
COPY scenario.py /app/scenario.py

RUN python -m py_compile /app/main.py /app/scenario.py

EXPOSE 8000 9108

CMD ["python", "main.py", "api"]
