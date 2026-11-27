FROM python:3.12-slim

WORKDIR /app

# Install system deps if needed for psycopg/fastembed
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

EXPOSE 8000
# Single worker to stay well within AWS t2.micro/t3.micro 1GB RAM budget
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
