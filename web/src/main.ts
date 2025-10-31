const SAMPLE_FRAME_DATA_URI =
    "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAQAAAAAYLlVAAAACXBIWXMAAAsTAAALEwEAmpwYAAABTUlEQVR4nO3WsQ3CMBQF0YZR0pniS3SBHotEpoRe7GuF/SciYHo9dZYkTfLZNVVRFl1FdYzfAz33/fWznrsCG2lraiiqqlgGkDkUFZaQcx5CiV5QTtAkQpokJ0CSGEmCSQJIQSYJJAkBBJgkkCSAEkGIBKIG0gsw7ob8s7/6IDdnh3oX1NwG8VS9/36uzxhpcJn/GrNqxabWWMwBLT/gRghOAqxCLv38saEhgoPQuPgAjT/w324jGIZh+JAnCmvYzu3/n6PvJEa3TBUnIFVQGryDxkS7jYimoMuNoMkmCSQJIQSYJJAkBBJgkkCSAEkGIBKIGECWEDSCzB5ZPnenjNX36PvPZV8C4D4ew3Efr2E1V9DqgW8BVnQL8AVd0C/AFXdAvwBV3QL8AVd0C/AFXdAvwBVd0GcAMbd7Gc2drXr/7EQAD//2n1pAVccawAAAAASUVORK5CYII=";

type FrameStats = {
  fps: number;
  resolution: string;
  mode: "raw" | "edges";
};

const stats: FrameStats = {
  fps: 15,
  resolution: "1280x720",
  mode: "edges"
};

const imageEl = document.getElementById("frameImage") as HTMLImageElement | null;
const fpsEl = document.getElementById("fps") as HTMLSpanElement | null;
const resolutionEl = document.getElementById("resolution") as HTMLSpanElement | null;
const modeEl = document.getElementById("mode") as HTMLSelectElement | null;

function renderFramePreview(): void {
  if (imageEl) {
    imageEl.src = SAMPLE_FRAME_DATA_URI;
  }
}

function renderStats(): void {
  if (fpsEl) {
    fpsEl.textContent = stats.fps.toFixed(1);
  }
  if (resolutionEl) {
    resolutionEl.textContent = stats.resolution;
  }
  if (modeEl) {
    modeEl.value = stats.mode;
  }
}

function handleModeChange(event: Event): void {
  const target = event.target as HTMLSelectElement;
  stats.mode = target.value as FrameStats["mode"];
}

function init(): void {
  renderFramePreview();
  renderStats();
  if (modeEl) {
    modeEl.addEventListener("change", handleModeChange);
  }
}

document.addEventListener("DOMContentLoaded", init);
