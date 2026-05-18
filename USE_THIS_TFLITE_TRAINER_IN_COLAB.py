import os
import shutil

import pandas as pd
import tensorflow as tf
from google.colab import files
from getpass import getpass

print("TensorFlow version:", tf.__version__)

print("Paste your Kaggle API token. It starts with KGAT_.")
kaggle_token = getpass("Kaggle API token: ")
os.environ["KAGGLE_API_TOKEN"] = kaggle_token.strip()

os.system("pip install --upgrade kaggle -q")
os.system(
    "kaggle datasets download "
    "-d paramaggarwal/fashion-product-images-small "
    "-p /content/fashion_data --unzip"
)

SOURCE_ROOT = "/content/fashion_data"
IMAGES_DIR = os.path.join(SOURCE_ROOT, "images")
CSV_PATH = os.path.join(SOURCE_ROOT, "styles.csv")
OUTPUT_DIR = "/content/clothing_dataset"
EXPORT_DIR = "/content/exported_model"

MAX_PER_CLASS = 700
IMG_SIZE = (224, 224)
BATCH_SIZE = 32
EPOCHS = 8

if os.path.exists(OUTPUT_DIR):
    shutil.rmtree(OUTPUT_DIR)
if os.path.exists(EXPORT_DIR):
    shutil.rmtree(EXPORT_DIR)
os.makedirs(OUTPUT_DIR, exist_ok=True)
os.makedirs(EXPORT_DIR, exist_ok=True)

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

print("Class counts:", counts)

train_ds = tf.keras.utils.image_dataset_from_directory(
    OUTPUT_DIR,
    validation_split=0.2,
    subset="training",
    seed=123,
    image_size=IMG_SIZE,
    batch_size=BATCH_SIZE,
)

val_ds = tf.keras.utils.image_dataset_from_directory(
    OUTPUT_DIR,
    validation_split=0.2,
    subset="validation",
    seed=123,
    image_size=IMG_SIZE,
    batch_size=BATCH_SIZE,
)

class_names = train_ds.class_names
print("Labels:", class_names)

with open(os.path.join(EXPORT_DIR, "labels.txt"), "w") as f:
    for label in class_names:
        f.write(label + "\n")

train_ds = train_ds.prefetch(tf.data.AUTOTUNE)
val_ds = val_ds.prefetch(tf.data.AUTOTUNE)

base_model = tf.keras.applications.MobileNetV2(
    input_shape=(224, 224, 3),
    include_top=False,
    weights="imagenet",
)
base_model.trainable = False

model = tf.keras.Sequential([
    tf.keras.layers.Input(shape=(224, 224, 3)),
    tf.keras.layers.Rescaling(1.0 / 255.0),
    base_model,
    tf.keras.layers.GlobalAveragePooling2D(),
    tf.keras.layers.Dropout(0.25),
    tf.keras.layers.Dense(len(class_names), activation="softmax"),
])

model.compile(
    optimizer=tf.keras.optimizers.Adam(learning_rate=0.0005),
    loss="sparse_categorical_crossentropy",
    metrics=["accuracy"],
)

model.fit(train_ds, validation_data=val_ds, epochs=EPOCHS)

converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
tflite_model = converter.convert()

model_path = os.path.join(EXPORT_DIR, "model.tflite")
labels_path = os.path.join(EXPORT_DIR, "labels.txt")

with open(model_path, "wb") as f:
    f.write(tflite_model)

print("Done. Downloading model.tflite and labels.txt")
files.download(model_path)
files.download(labels_path)
