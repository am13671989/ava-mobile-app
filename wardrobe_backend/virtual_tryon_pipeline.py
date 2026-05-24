from __future__ import annotations

import math
from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from typing import Iterable, Optional

import cv2
import numpy as np

try:
    import mediapipe as mp
except Exception:  # Optional dependency.
    mp = None

try:
    from rembg import remove as rembg_remove
except Exception:  # Optional dependency.
    rembg_remove = None

try:
    from ultralytics import YOLO
except Exception:  # Optional dependency.
    YOLO = None


class FitPart(str, Enum):
    UPPER = "upper"
    LOWER = "lower"
    FULL = "full"


@dataclass(frozen=True)
class TryOnConfig:
    min_pose_confidence: float = 0.45
    cloth_width_scale: float = 1.32
    cloth_height_scale: float = 1.20
    upper_y_offset: float = -0.05
    lower_y_offset: float = -0.03
    segmentation_threshold: int = 16
    alpha_threshold: int = 12
    edge_feather: int = 3
    use_yolo_pose: bool = False
    yolo_pose_model: str = "yolov8n-pose.pt"


@dataclass(frozen=True)
class BodyLandmarks:
    left_shoulder: np.ndarray
    right_shoulder: np.ndarray
    left_hip: np.ndarray
    right_hip: np.ndarray
    neck_center: np.ndarray
    torso_center: np.ndarray
    body_angle: float
    confidence: float
    source: str

    @property
    def shoulder_width(self) -> float:
        return float(np.linalg.norm(self.right_shoulder - self.left_shoulder))

    @property
    def hip_width(self) -> float:
        return float(np.linalg.norm(self.right_hip - self.left_hip))

    @property
    def torso_height(self) -> float:
        hip_center = (self.left_hip + self.right_hip) / 2.0
        return float(np.linalg.norm(hip_center - self.neck_center))


@dataclass(frozen=True)
class BodyMask:
    mask: np.ndarray
    torso_mask: np.ndarray
    arm_mask: np.ndarray
    contour: np.ndarray


@dataclass(frozen=True)
class ProcessedCloth:
    rgba: np.ndarray
    mask: np.ndarray
    contour: np.ndarray
    centerline_x: float
    neck_anchor: np.ndarray


@dataclass(frozen=True)
class TryOnResult:
    image: np.ndarray
    landmarks: BodyLandmarks
    body_mask: BodyMask
    cloth: ProcessedCloth
    transform: np.ndarray


def read_image(path: str | Path, unchanged: bool = True) -> np.ndarray:
    flag = cv2.IMREAD_UNCHANGED if unchanged else cv2.IMREAD_COLOR
    image = cv2.imread(str(path), flag)
    if image is None:
        raise FileNotFoundError(f"Image not found: {path}")
    return ensure_rgba(image)


def write_image(path: str | Path, image: np.ndarray) -> None:
    output = ensure_bgra_for_write(image)
    ok = cv2.imwrite(str(path), output)
    if not ok:
        raise OSError(f"Could not write image: {path}")


def ensure_rgba(image: np.ndarray) -> np.ndarray:
    if image.ndim != 3:
        raise ValueError("Expected a color image")
    if image.shape[2] == 4:
        return cv2.cvtColor(image, cv2.COLOR_BGRA2RGBA)
    if image.shape[2] == 3:
        rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
        alpha = np.full(rgb.shape[:2] + (1,), 255, dtype=np.uint8)
        return np.concatenate([rgb, alpha], axis=2)
    raise ValueError("Expected 3 or 4 channel image")


def ensure_bgra_for_write(image: np.ndarray) -> np.ndarray:
    if image.shape[2] == 4:
        return cv2.cvtColor(image, cv2.COLOR_RGBA2BGRA)
    return cv2.cvtColor(image, cv2.COLOR_RGB2BGR)


class BodyDetector:
    def __init__(self, config: TryOnConfig):
        self.config = config
        self._yolo_model = None

    def detect(self, image_rgba: np.ndarray) -> BodyLandmarks:
        errors: list[str] = []
        if mp is not None:
            try:
                result = self._detect_mediapipe_pose(image_rgba)
                if result.confidence >= self.config.min_pose_confidence:
                    return result
                errors.append(f"mediapipe low confidence {result.confidence:.2f}")
            except Exception as exc:
                errors.append(f"mediapipe failed: {exc}")

        if self.config.use_yolo_pose and YOLO is not None:
            try:
                result = self._detect_yolo_pose(image_rgba)
                if result.confidence >= self.config.min_pose_confidence:
                    return result
                errors.append(f"yolo low confidence {result.confidence:.2f}")
            except Exception as exc:
                errors.append(f"yolo failed: {exc}")

        fallback = self._detect_from_silhouette(image_rgba)
        if fallback.confidence > 0:
            return fallback
        joined = "; ".join(errors) if errors else "no detector available"
        raise ValueError(f"No usable body landmarks detected: {joined}")

    def _detect_mediapipe_pose(self, image_rgba: np.ndarray) -> BodyLandmarks:
        pose_module = mp.solutions.pose
        rgb = image_rgba[:, :, :3]
        height, width = rgb.shape[:2]
        with pose_module.Pose(static_image_mode=True, model_complexity=2, enable_segmentation=True) as pose:
            output = pose.process(rgb)
        if not output.pose_landmarks:
            raise ValueError("MediaPipe returned no pose landmarks")
        lm = output.pose_landmarks.landmark

        def point(name):
            item = lm[name.value]
            return np.array([item.x * width, item.y * height], dtype=np.float32), float(item.visibility)

        left_shoulder, c1 = point(pose_module.PoseLandmark.LEFT_SHOULDER)
        right_shoulder, c2 = point(pose_module.PoseLandmark.RIGHT_SHOULDER)
        left_hip, c3 = point(pose_module.PoseLandmark.LEFT_HIP)
        right_hip, c4 = point(pose_module.PoseLandmark.RIGHT_HIP)
        return build_landmarks(left_shoulder, right_shoulder, left_hip, right_hip, float(np.mean([c1, c2, c3, c4])), "mediapipe_pose")

    def _detect_yolo_pose(self, image_rgba: np.ndarray) -> BodyLandmarks:
        if self._yolo_model is None:
            self._yolo_model = YOLO(self.config.yolo_pose_model)
        rgb = image_rgba[:, :, :3]
        result = self._yolo_model.predict(rgb, verbose=False)[0]
        if result.keypoints is None or len(result.keypoints.xy) == 0:
            raise ValueError("YOLO returned no keypoints")
        keypoints = result.keypoints.xy[0].cpu().numpy()
        conf = result.keypoints.conf[0].cpu().numpy() if result.keypoints.conf is not None else np.ones((keypoints.shape[0],), dtype=np.float32)
        # COCO order: left shoulder 5, right shoulder 6, left hip 11, right hip 12.
        return build_landmarks(keypoints[5], keypoints[6], keypoints[11], keypoints[12], float(np.mean([conf[5], conf[6], conf[11], conf[12]])), "yolo_pose")

    def _detect_from_silhouette(self, image_rgba: np.ndarray) -> BodyLandmarks:
        mask = alpha_or_color_mask(image_rgba, self.config.segmentation_threshold, self.config.alpha_threshold)
        contour = largest_contour(mask)
        if contour is None:
            return build_landmarks(np.zeros(2), np.zeros(2), np.zeros(2), np.zeros(2), 0.0, "silhouette_failed")
        x, y, w, h = cv2.boundingRect(contour)
        left_shoulder = np.array([x + w * 0.24, y + h * 0.25], dtype=np.float32)
        right_shoulder = np.array([x + w * 0.76, y + h * 0.25], dtype=np.float32)
        left_hip = np.array([x + w * 0.34, y + h * 0.56], dtype=np.float32)
        right_hip = np.array([x + w * 0.66, y + h * 0.56], dtype=np.float32)
        return build_landmarks(left_shoulder, right_shoulder, left_hip, right_hip, 0.35, "silhouette")


def build_landmarks(left_shoulder: np.ndarray, right_shoulder: np.ndarray, left_hip: np.ndarray, right_hip: np.ndarray, confidence: float, source: str) -> BodyLandmarks:
    neck_center = (left_shoulder + right_shoulder) / 2.0
    hip_center = (left_hip + right_hip) / 2.0
    torso_center = (neck_center + hip_center) / 2.0
    shoulder_vec = right_shoulder - left_shoulder
    body_angle = math.degrees(math.atan2(float(shoulder_vec[1]), float(shoulder_vec[0])))
    return BodyLandmarks(left_shoulder, right_shoulder, left_hip, right_hip, neck_center, torso_center, body_angle, confidence, source)


class BodySegmenter:
    def __init__(self, config: TryOnConfig):
        self.config = config

    def segment(self, image_rgba: np.ndarray, landmarks: BodyLandmarks) -> BodyMask:
        body_mask = alpha_or_color_mask(image_rgba, self.config.segmentation_threshold, self.config.alpha_threshold)
        body_mask = clean_mask(body_mask)
        torso_mask = self._torso_mask(body_mask.shape, landmarks)
        arm_mask = cv2.subtract(body_mask, torso_mask)
        contour = largest_contour(body_mask)
        if contour is None:
            contour = np.empty((0, 1, 2), dtype=np.int32)
        return BodyMask(body_mask, torso_mask, arm_mask, contour)

    def _torso_mask(self, shape: tuple[int, int], landmarks: BodyLandmarks) -> np.ndarray:
        points = np.array([
            landmarks.left_shoulder,
            landmarks.right_shoulder,
            landmarks.right_hip,
            landmarks.left_hip,
        ], dtype=np.int32)
        mask = np.zeros(shape, dtype=np.uint8)
        cv2.fillConvexPoly(mask, points, 255)
        kernel = np.ones((9, 9), dtype=np.uint8)
        return cv2.dilate(mask, kernel, iterations=1)


class ClothingProcessor:
    def __init__(self, config: TryOnConfig):
        self.config = config

    def process(self, cloth_rgba: np.ndarray) -> ProcessedCloth:
        rgba = self._remove_background(cloth_rgba)
        rgba = crop_to_alpha(rgba, self.config.alpha_threshold)
        mask = (rgba[:, :, 3] > self.config.alpha_threshold).astype(np.uint8) * 255
        mask = clean_mask(mask)
        contour = largest_contour(mask)
        if contour is None:
            contour = np.empty((0, 1, 2), dtype=np.int32)
            centerline_x = rgba.shape[1] / 2.0
            neck_anchor = np.array([centerline_x, 0.0], dtype=np.float32)
        else:
            moments = cv2.moments(contour)
            centerline_x = rgba.shape[1] / 2.0 if moments["m00"] == 0 else float(moments["m10"] / moments["m00"])
            neck_anchor = self._estimate_neck_anchor(mask, centerline_x)
        return ProcessedCloth(rgba, mask, contour, centerline_x, neck_anchor)

    def _remove_background(self, cloth_rgba: np.ndarray) -> np.ndarray:
        if cloth_rgba.shape[2] == 4 and np.any(cloth_rgba[:, :, 3] < 250):
            return cloth_rgba
        if rembg_remove is not None:
            try:
                bgra = cv2.cvtColor(cloth_rgba, cv2.COLOR_RGBA2BGRA)
                removed = rembg_remove(bgra)
                return ensure_rgba(np.asarray(removed))
            except Exception:
                pass
        mask = alpha_or_color_mask(cloth_rgba, self.config.segmentation_threshold, self.config.alpha_threshold)
        rgba = cloth_rgba.copy()
        rgba[:, :, 3] = mask
        return rgba

    def _estimate_neck_anchor(self, mask: np.ndarray, centerline_x: float) -> np.ndarray:
        ys, xs = np.where(mask > 0)
        if len(xs) == 0:
            return np.array([centerline_x, 0], dtype=np.float32)
        top = int(ys.min())
        top_band = xs[ys <= top + max(8, mask.shape[0] // 18)]
        if len(top_band) == 0:
            return np.array([centerline_x, top], dtype=np.float32)
        return np.array([float(np.mean(top_band)), float(top)], dtype=np.float32)


class GarmentFitter:
    def __init__(self, config: TryOnConfig):
        self.config = config

    def fit(self, avatar_rgba: np.ndarray, body: BodyLandmarks, body_mask: BodyMask, cloth: ProcessedCloth, fit_part: FitPart = FitPart.UPPER) -> TryOnResult:
        transform = self._transform_for(body, cloth, fit_part)
        warped = cv2.warpAffine(
            cloth.rgba,
            transform,
            (avatar_rgba.shape[1], avatar_rgba.shape[0]),
            flags=cv2.INTER_LINEAR,
            borderMode=cv2.BORDER_CONSTANT,
            borderValue=(0, 0, 0, 0),
        )
        composed = self._compose_with_occlusion(avatar_rgba, warped, body_mask, fit_part)
        return TryOnResult(composed, body, body_mask, cloth, transform)

    def _transform_for(self, body: BodyLandmarks, cloth: ProcessedCloth, fit_part: FitPart) -> np.ndarray:
        shoulder_width = max(body.shoulder_width, 1.0)
        torso_height = max(body.torso_height, 1.0)
        cloth_h, cloth_w = cloth.rgba.shape[:2]
        if fit_part == FitPart.LOWER:
            target_w = max(body.hip_width, shoulder_width * 0.65) * 1.18
            target_h = torso_height * 1.35
            center = (body.left_hip + body.right_hip) / 2.0 + np.array([0.0, torso_height * 0.50], dtype=np.float32)
            y_offset = self.config.lower_y_offset
        elif fit_part == FitPart.FULL:
            target_w = shoulder_width * self.config.cloth_width_scale
            target_h = torso_height * 2.05
            center = body.torso_center + np.array([0.0, torso_height * 0.45], dtype=np.float32)
            y_offset = 0.0
        else:
            target_w = shoulder_width * self.config.cloth_width_scale
            target_h = torso_height * self.config.cloth_height_scale
            center = body.neck_center + np.array([0.0, torso_height * (0.50 + self.config.upper_y_offset)], dtype=np.float32)
            y_offset = self.config.upper_y_offset
        scale = min(target_w / max(cloth_w, 1), target_h / max(cloth_h, 1))
        anchor = cloth.neck_anchor if fit_part == FitPart.UPPER else np.array([cloth.centerline_x, cloth_h * 0.35], dtype=np.float32)
        matrix = cv2.getRotationMatrix2D((float(anchor[0]), float(anchor[1])), body.body_angle, scale)
        desired_anchor = center + np.array([0.0, torso_height * y_offset], dtype=np.float32)
        current_anchor = np.array([
            matrix[0, 0] * anchor[0] + matrix[0, 1] * anchor[1] + matrix[0, 2],
            matrix[1, 0] * anchor[0] + matrix[1, 1] * anchor[1] + matrix[1, 2],
        ], dtype=np.float32)
        delta = desired_anchor - current_anchor
        matrix[:, 2] += delta
        return matrix.astype(np.float32)

    def _compose_with_occlusion(self, avatar_rgba: np.ndarray, warped_rgba: np.ndarray, body_mask: BodyMask, fit_part: FitPart) -> np.ndarray:
        base_rgb = avatar_rgba[:, :, :3].astype(np.float32)
        cloth_rgb = warped_rgba[:, :, :3].astype(np.float32)
        alpha = (warped_rgba[:, :, 3].astype(np.float32) / 255.0)
        if self.config.edge_feather > 0:
            alpha = cv2.GaussianBlur(alpha, (0, 0), self.config.edge_feather)
        if fit_part == FitPart.UPPER:
            arm_front = (body_mask.arm_mask.astype(np.float32) / 255.0) * 0.80
            alpha = alpha * (1.0 - arm_front)
        result_rgb = cloth_rgb * alpha[:, :, None] + base_rgb * (1.0 - alpha[:, :, None])
        result = avatar_rgba.copy()
        result[:, :, :3] = np.clip(result_rgb, 0, 255).astype(np.uint8)
        result[:, :, 3] = np.maximum(avatar_rgba[:, :, 3], warped_rgba[:, :, 3])
        return result


class VirtualTryOnPipeline:
    def __init__(self, config: Optional[TryOnConfig] = None):
        self.config = config or TryOnConfig()
        self.detector = BodyDetector(self.config)
        self.segmenter = BodySegmenter(self.config)
        self.clothing = ClothingProcessor(self.config)
        self.fitter = GarmentFitter(self.config)

    def run(self, avatar: np.ndarray, cloth: np.ndarray, fit_part: FitPart = FitPart.UPPER) -> TryOnResult:
        avatar_rgba = ensure_rgba(avatar)
        cloth_rgba = ensure_rgba(cloth)
        landmarks = self.detector.detect(avatar_rgba)
        body_mask = self.segmenter.segment(avatar_rgba, landmarks)
        processed_cloth = self.clothing.process(cloth_rgba)
        return self.fitter.fit(avatar_rgba, landmarks, body_mask, processed_cloth, fit_part)

    def run_files(self, avatar_path: str | Path, cloth_path: str | Path, output_path: str | Path, fit_part: FitPart = FitPart.UPPER) -> TryOnResult:
        avatar = read_image(avatar_path)
        cloth = read_image(cloth_path)
        result = self.run(avatar, cloth, fit_part)
        write_image(output_path, result.image)
        return result

    def run_batch(self, jobs: Iterable[tuple[str | Path, str | Path, str | Path, FitPart]]) -> list[TryOnResult]:
        return [self.run_files(avatar, cloth, output, part) for avatar, cloth, output, part in jobs]


def alpha_or_color_mask(image_rgba: np.ndarray, color_threshold: int, alpha_threshold: int) -> np.ndarray:
    alpha = image_rgba[:, :, 3]
    if np.any(alpha < 250):
        return (alpha > alpha_threshold).astype(np.uint8) * 255
    rgb = image_rgba[:, :, :3].astype(np.float32)
    corners = np.array([rgb[0, 0], rgb[0, -1], rgb[-1, 0], rgb[-1, -1]], dtype=np.float32)
    background = np.median(corners, axis=0)
    diff = np.abs(rgb - background).sum(axis=2)
    return (diff > color_threshold).astype(np.uint8) * 255


def clean_mask(mask: np.ndarray) -> np.ndarray:
    kernel = np.ones((5, 5), dtype=np.uint8)
    mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, kernel, iterations=1)
    mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, kernel, iterations=2)
    return mask


def largest_contour(mask: np.ndarray) -> Optional[np.ndarray]:
    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not contours:
        return None
    return max(contours, key=cv2.contourArea)


def crop_to_alpha(rgba: np.ndarray, alpha_threshold: int) -> np.ndarray:
    ys, xs = np.where(rgba[:, :, 3] > alpha_threshold)
    if len(xs) == 0 or len(ys) == 0:
        return rgba
    left, right = int(xs.min()), int(xs.max()) + 1
    top, bottom = int(ys.min()), int(ys.max()) + 1
    pad_x = max(4, int((right - left) * 0.05))
    pad_y = max(4, int((bottom - top) * 0.05))
    left = max(0, left - pad_x)
    top = max(0, top - pad_y)
    right = min(rgba.shape[1], right + pad_x)
    bottom = min(rgba.shape[0], bottom + pad_y)
    return rgba[top:bottom, left:right]


def fit_cloth_with_pose(
    avatar_path: str | Path,
    cloth_path: str | Path,
    output_path: str | Path = "avatar_with_cloth.png",
    fit_part: FitPart = FitPart.UPPER,
    config: Optional[TryOnConfig] = None,
) -> np.ndarray:
    pipeline = VirtualTryOnPipeline(config)
    result = pipeline.run_files(avatar_path, cloth_path, output_path, fit_part)
    return result.image
