FROM python:3
ADD pvc_snapshot.py .
RUN pip install google-api-python-client
RUN pip install kubernetes
RUN pip install requests
CMD ["python", "./pvc_snapshot.py"]
