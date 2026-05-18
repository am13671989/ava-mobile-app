# Run this file in Google Colab.
# It trains a MediaPipe/TensorFlow Lite clothing classifier and exports model.tflite.

import os
import shutil

import pandas as pd
from google.colab import files

print("Installing dependencies...")
os.system("pip install --upgrade pip -q")
os.system("pip install mediapipe-model-maker kaggle -q")

from mediapipe_model_maker import image_classifier

print("Upload kaggle.json when the upload button appears.")
files.upload()

os.system("mkdir -p ~/.kaggle")
os.system("cp kaggle.json ~/.kaggle/kaggle.json")
os.system("chmod 600 ~/.kaggle/kaggle.json")
os.system(
    "kaggle datasets download "
    "-d paramaggarwal/fashion-product-images-small "
    "-p /content/fashion_data --unzip"
)

SOURCE_ROOT = "/content/fashion_data"
IMAGES_DIR = os.path.join(SOURCE_ROOT, "images")
CSV_PATH = os.path.join(SOURCE_ROOT, "styles.csv")
OUTPUT_DIR = "/content/clothing_dataset"
MAX_PER_CLASS = 900

if os.path.exists(OUTPUT_DIR):
    shutil.rmtree(OUTPUT_DIR)
os.makedirs(OUTPUT_DIR, exist_ok=True)

label_map = {
    "Tshirts": "T-Shirt",
    "Shirts": "Shirt",
    "Jeans": "Pants",
    "Trousers": "Pants",
    "Track Pants": "Pants",
    "Casual Shoes": "Shoes",
    "Sports Shoes": "Shoes",
    "Formal Shoes": "Shoes",
    "Heels": "Shoes",
    "Flats": "Shoes",
    "Sandals": "Shoes",
    "Flip Flops": "Shoes",
    "Socks": "Socks",
    "Jackets": "Jacket",
    "Blazers": "Jacket",
    "Sweaters": "Jacket",
    "Sweatshirts": "Hoodie",
    "Shorts": "Shorts",
    "Dresses": "Dress",
    "Skirts": "Skirt",
    "Handbags": "Bag",
    "Backpacks": "Bag",
    "Caps": "Hat",
}

df = pd.read_csv(CSV_PATH, on_bad_lines="skip")
df = df[["id", "articleType"]].dropna()
df["label"] = df["articleType"].map(label_map)
df = df.dropna(subset=["label"])

counts = {}
for _, row in df.iterrows():
    label = row["label"]
    if counts.get(label, 0) >= MAX_PER_CLASS:
        continue
    image_path = os.path.join(IMAGES_DIR, f"{int(row['id'])}.jpg")
    if not os.path.exists(image_path):
        continue
    label_dir = os.path.join(OUTPUT_DIR, label)
    os.makedirs(label_dir, exist_ok=True)
    shutil.copy(image_path, os.path.join(label_dir, os.path.basename(image_path)))
    counts[label] = counts.get(label, 0) + 1

print("Dataset created:", OUTPUT_DIR)
print("Class counts:", counts)

data = image_classifier.Dataset.from_folder(OUTPUT_DIR)
train_data, remaining_data = data.split(0.8)
test_data, validation_data = remaining_data.split(0.5)

spec = image_classifier.SupportedModels.MOBILENET_V2
hparams = image_classifier.HParams(
    export_dir="/content/exported_model",
    epochs=12,
    batch_size=32,
)
options = image_classifier.ImageClassifierOptions(
    supported_model=spec,
    hparams=hparams,
)

print("Training model...")
model = image_classifier.ImageClassifier.create(
    train_data=train_data,
    validation_data=validation_data,
    options=options,
)

print("Evaluating model...")
loss, acc = model.evaluate(test_data)
print("Test loss:", loss)
print("Test accuracy:", acc)

print("Exporting model.tflite...")
model.export_model(model_name="model.tflite")
os.system("ls -lh /content/exported_model")
files.download("/content/exported_model/model.tflite")
