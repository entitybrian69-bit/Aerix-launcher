const container = document.getElementById("skin-container");

const getWidth = () => container.clientWidth || window.innerWidth || 240;
const getHeight = () => container.clientHeight || window.innerHeight || 340;

function notifyReady(ok, reason) {
    try {
        if (window.AndroidSkinBridge) {
            if (ok) {
                window.AndroidSkinBridge.onViewerReady();
            } else {
                window.AndroidSkinBridge.onViewerError(String(reason || "webgl_unavailable"));
            }
        }
    } catch (_) {}
}

let skinViewer = null;
try {
    skinViewer = new skinview3d.SkinViewer({
        canvas: document.createElement("canvas"),
        width: getWidth(),
        height: getHeight()
    });
    skinViewer.pixelRatio = Math.min(window.devicePixelRatio || 1, 2);
    skinViewer.renderPaused = true;
    container.appendChild(skinViewer.canvas);
} catch (err) {
    skinViewer = null;
    notifyReady(false, err && err.message ? err.message : err);
}

class IdleAnimation extends skinview3d.PlayerAnimation {
    constructor() {
        super();
        this.elapsed = 0;
        this.maxDelta = 0.05;
    }

    getCapeRotation(t) {
        const wave1 = Math.sin(t * Math.PI * 2 * 0.3);
        const wave2 = Math.sin(t * Math.PI * 2 * 0.25) * 0.3;

        let normalized = (wave1 + wave2 + 1.3) / 2.6;
        normalized = Math.max(0, Math.min(1));

        const angleX = 0.15 + normalized * 0.15;

        const waveZ = Math.sin(t * Math.PI * 2 * 0.35);
        const angleZ = 0.03 + (waveZ + 1) / 2 * 0.03;

        return { x: angleX, z: angleZ };
    }

    animate(player, delta) {
        const dt = Math.min(delta, this.maxDelta);
        this.elapsed += dt;

        const t = this.elapsed;

        player.skin.body.position.y = Math.sin(t * 2.2) * -0.1 - 6.1;
        player.skin.head.position.y = Math.sin(t * 2.2) * -0.1 - 0.1;

        player.skin.body.rotation.x = Math.sin(t * 2.2) * 0.02;
        player.skin.body.rotation.z = Math.sin(t * 1.5) * 0.01;

        player.skin.head.rotation.y = Math.sin(t * 0.6) * 0.1;
        player.skin.head.rotation.x = Math.sin(t * 2.2) * 0.04;
        player.skin.head.rotation.z = Math.sin(t * 1.5) * 0.01;

        const armX = Math.sin(t * 2.2) * 0.05;
        const armZ = (Math.sin(t * 1.8) + 1) / 2 * -0.05;

        player.skin.rightArm.rotation.x = armX;
        player.skin.leftArm.rotation.x = armX;

        player.skin.rightArm.rotation.z = armZ;
        player.skin.leftArm.rotation.z = -armZ;

        if (player.cape) {
            const capeRot = this.getCapeRotation(t);
            player.cape.rotation.x = capeRot.x;
            player.cape.rotation.z = capeRot.z;
        }
    }

    reset() {
        this.elapsed = 0;
    }
}

function startAnim(name, speed) {
    if (!skinViewer || typeof name !== 'string' || name.trim() === '') return;

    let speed0 = null;
    if (typeof speed === 'number' && !isNaN(speed) && speed > 0) {
        speed0 = speed;
    }

    let anim;
    switch (name) {
        case "DefaultIdle":
            anim = new skinview3d.IdleAnimation();
            break;
        case "NewIdle":
            anim = new IdleAnimation();
            break;
        case "Walking":
            anim = new skinview3d.WalkingAnimation();
            break;
        case "Running":
            anim = new skinview3d.RunningAnimation();
            break;
        case "Flying":
            anim = new skinview3d.FlyingAnimation();
            break;
        case "Wave":
            anim = new skinview3d.WaveAnimation();
            break;
        case "Crouch":
            anim = new skinview3d.CrouchAnimation();
            break;
        case "Hit":
            anim = new skinview3d.HitAnimation();
            break;
        default:
            return;
    }

    skinViewer.animation = anim;
    if (speed0 !== null && skinViewer.animation) {
        skinViewer.animation.speed = speed0;
    }
    skinViewer.renderPaused = false;
}

let defaultCameraPos = null;
let defaultControlsTarget = null;

function updateDefaultCameraPosition() {
    if (!skinViewer) return;
    if (!defaultCameraPos) defaultCameraPos = skinViewer.camera.position.clone();
    else defaultCameraPos.copy(skinViewer.camera.position);
    if (!defaultControlsTarget) defaultControlsTarget = skinViewer.controls.target.clone();
    else defaultControlsTarget.copy(skinViewer.controls.target);
}

function setAzimuthAndPitch(azimuthDeg, pitchDeg, distance = 60) {
    if (!skinViewer) return;
    const controls = skinViewer.controls;
    const target = controls.target;

    const azimuth = azimuthDeg * Math.PI / 180;
    const pitch = pitchDeg * Math.PI / 180;

    const x = distance * Math.cos(pitch) * Math.sin(azimuth);
    const y = distance * Math.sin(pitch);
    const z = distance * Math.cos(pitch) * Math.cos(azimuth);

    skinViewer.camera.position.set(target.x + x, target.y + y, target.z + z);
    controls.update();
    if (skinViewer.renderPaused) skinViewer.render();

    updateDefaultCameraPosition();
}

function resize() {
    if (!skinViewer) return;
    const w = getWidth();
    const h = getHeight();
    if (w > 4 && h > 4) {
        skinViewer.width = w;
        skinViewer.height = h;
        skinViewer.render();
    }
}

function isWebGlAlive() {
    if (!skinViewer || !skinViewer.renderer) return false;
    try {
        const gl = typeof skinViewer.renderer.getContext === "function"
            ? skinViewer.renderer.getContext()
            : null;
        if (gl && typeof gl.isContextLost === "function" && gl.isContextLost()) {
            return false;
        }
    } catch (_) {
        return false;
    }
    return true;
}

function loadSkin(skinUrl, model = "auto-detect") {
    if (!skinViewer || !isWebGlAlive()) {
        notifyReady(false, "no_viewer");
        return;
    }
    Promise.resolve(skinViewer.loadSkin(skinUrl, { model: model }))
        .then(() => {
            const finishPaint = () => {
                if (!isWebGlAlive()) {
                    try {
                        if (window.AndroidSkinBridge) window.AndroidSkinBridge.onSkinPainted(false);
                    } catch (_) {}
                    return;
                }
                resize();
                skinViewer.render();
                try {
                    if (window.AndroidSkinBridge) window.AndroidSkinBridge.onSkinPainted(true);
                } catch (_) {}
            };
            if (container.clientWidth > 8 && container.clientHeight > 8) {
                finishPaint();
            } else {
                setTimeout(finishPaint, 80);
            }
        })
        .catch(error => {
            console.warn("Unable to render skin preview", error);
            try {
                if (window.AndroidSkinBridge) window.AndroidSkinBridge.onSkinPainted(false);
            } catch (_) {}
        });
}

function loadCape(capeUrl) {
    if (!skinViewer) return;
    Promise.resolve(skinViewer.loadCape(capeUrl))
        .then(() => skinViewer.render())
        .catch(error => console.warn("Unable to render cape preview", error));
}

function setRenderPaused(paused) {
    if (!skinViewer) return;
    const shouldPause = paused !== false;
    skinViewer.renderPaused = shouldPause;
    if (shouldPause) skinViewer.render();
}

function setInteractionEnabled(enabled) {
    if (!skinViewer) return;
    const interactive = enabled !== false;
    skinViewer.controls.enableRotate = interactive;
    container.style.pointerEvents = interactive ? 'auto' : 'none';
}

if (skinViewer) {
    skinViewer.controls.enableRotate = true;
    skinViewer.controls.enableZoom = false;
    skinViewer.controls.enablePan = false;

    skinViewer.controls.addEventListener("change", () => {
        if (skinViewer.renderPaused) skinViewer.render();
    });

    setAzimuthAndPitch(0, 10);

    let resetAnimationId = null;

    container.addEventListener("dblclick", () => {
        if (!defaultCameraPos || !defaultControlsTarget) return;
        if (resetAnimationId) cancelAnimationFrame(resetAnimationId);

        const DECAY = 6;
        let lastTime = performance.now();

        const animateReset = (now) => {
            const rawDt = (now - lastTime) / 1000;
            const dt = Math.min(rawDt, 0.1);
            lastTime = now;
            const alpha = 1 - Math.exp(-DECAY * dt);

            const t = skinViewer.controls.target;
            const dst = defaultControlsTarget;
            t.x += (dst.x - t.x) * alpha;
            t.y += (dst.y - t.y) * alpha;
            t.z += (dst.z - t.z) * alpha;

            const cam = skinViewer.camera.position;
            const ox = cam.x - t.x;
            const oy = cam.y - t.y;
            const oz = cam.z - t.z;

            const dx = defaultCameraPos.x - dst.x;
            const dy = defaultCameraPos.y - dst.y;
            const dz = defaultCameraPos.z - dst.z;

            const curR = Math.sqrt(ox * ox + oy * oy + oz * oz);
            const defR = Math.sqrt(dx * dx + dy * dy + dz * dz);
            const curPhi = Math.asin(Math.max(-1, Math.min(1, oy / curR)));
            const defPhi = Math.asin(Math.max(-1, Math.min(1, dy / defR)));
            const curTheta = Math.atan2(oz, ox);
            const defTheta = Math.atan2(dz, dx);

            let dTheta = defTheta - curTheta;
            if (dTheta > Math.PI) dTheta -= 2 * Math.PI;
            if (dTheta < -Math.PI) dTheta += 2 * Math.PI;

            const nextR = curR + (defR - curR) * alpha;
            const nextPhi = curPhi + (defPhi - curPhi) * alpha;
            const nextTheta = curTheta + dTheta * alpha;

            const cosPhi = Math.cos(nextPhi);
            cam.x = t.x + nextR * cosPhi * Math.cos(nextTheta);
            cam.y = t.y + nextR * Math.sin(nextPhi);
            cam.z = t.z + nextR * cosPhi * Math.sin(nextTheta);

            skinViewer.controls.update();

            const dpx = cam.x - defaultCameraPos.x;
            const dpy = cam.y - defaultCameraPos.y;
            const dpz = cam.z - defaultCameraPos.z;
            const distPos = Math.sqrt(dpx * dpx + dpy * dpy + dpz * dpz);

            const ttx = t.x - dst.x;
            const tty = t.y - dst.y;
            const ttz = t.z - dst.z;
            const distTarget = Math.sqrt(ttx * ttx + tty * tty + ttz * ttz);

            if (distPos > 0.05 || distTarget > 0.05) {
                resetAnimationId = requestAnimationFrame(animateReset);
            } else {
                cam.x = defaultCameraPos.x;
                cam.y = defaultCameraPos.y;
                cam.z = defaultCameraPos.z;
                t.x = dst.x; t.y = dst.y; t.z = dst.z;
                skinViewer.controls.update();
                resetAnimationId = null;
            }
        };

        resetAnimationId = requestAnimationFrame(animateReset);
    });

    skinViewer.controls.addEventListener("start", () => {
        if (resetAnimationId) {
            cancelAnimationFrame(resetAnimationId);
            resetAnimationId = null;
        }
    });

    skinViewer.canvas.addEventListener("webglcontextlost", (e) => {
        if (e && typeof e.preventDefault === "function") e.preventDefault();
        try {
            if (window.AndroidSkinBridge) window.AndroidSkinBridge.onViewerError("webgl_context_lost");
        } catch (_) {}
    });

    window.addEventListener("resize", resize);
    if (typeof ResizeObserver !== "undefined") {
        new ResizeObserver(() => resize()).observe(container);
    }
    setTimeout(resize, 60);
    setTimeout(resize, 250);
    setTimeout(resize, 650);

    setInteractionEnabled(true);
    notifyReady(true, null);
}
