FROM python:3.12-slim

WORKDIR /app

# CPU 전용 torch 먼저 설치 (CUDA 버전보다 이미지가 훨씬 작음)
RUN pip install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cpu

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
