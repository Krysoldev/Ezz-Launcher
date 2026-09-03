import * as skinview3d from 'skinview3d';

export const PRESET_SKINS = [
  {
    id: 'steve',
    name: 'Classic Steve',
    model: 'default',
    url: 'https://textures.minecraft.net/texture/1a2c964a16102fca35523149a469490a618e7e17e47fbc16b8a8b13be82f9d'
  },
  {
    id: 'alex',
    name: 'Classic Alex',
    model: 'slim',
    url: 'https://textures.minecraft.net/texture/46761f1c7d242636ab29c0f9947936a71569ca9c2bfb15b1fb28469d749a43a0'
  },
  {
    id: 'cyber_ninja',
    name: 'Cyber Ninja',
    model: 'default',
    url: 'https://textures.minecraft.net/texture/2bbbc1d2147649bbba5b0a802a42be98'
  },
  {
    id: 'neon_runner',
    name: 'Neon Runner',
    model: 'slim',
    url: 'https://textures.minecraft.net/texture/415d86ca3f1f0a3e87498c4ad0ffcaad7c6d66e7614d3df2dd4b3017a4197db3'
  }
];

export class SkinStudioViewer {
  constructor(canvasElement, options = {}) {
    this.canvas = canvasElement;
    this.initialModel = options.model === 'slim' ? 'slim' : 'default';
    this.initialSkin = options.skin || PRESET_SKINS[0].url;

    // Initialize skinview3d viewer
    this.viewer = new skinview3d.SkinViewer({
      canvas: canvasElement,
      width: options.width || 360,
      height: options.height || 480,
      skin: this.initialSkin,
      model: this.initialModel
    });

    // Camera initial position
    this.resetCamera();

    // Nearest-neighbor / pixel-perfect rendering
    if (this.viewer.renderer) {
      this.viewer.renderer.pixelRatio = window.devicePixelRatio || 1;
    }

    // Interactive Orbit Controls:
    // Natural rotation: dragging right rotates model right; scrolling zooms
    // NO auto-rotation by default
    this.viewer.autoRotate = false;

    if (this.viewer.controls) {
      this.viewer.controls.enableRotate = true;
      this.viewer.controls.enableZoom = true;
      this.viewer.controls.enablePan = false;
      this.viewer.controls.minDistance = 30;
      this.viewer.controls.maxDistance = 120;
    }

    // Default Animation: Idle
    this.animation = this.viewer.animations.add(skinview3d.IdleAnimation);
    this.animation.speed = 0.8;
  }

  loadSkin(url, model = 'default') {
    this.currentSkinUrl = url;
    this.currentModel = model === 'slim' ? 'slim' : 'default';
    this.viewer.loadSkin(url, {
      model: this.currentModel
    });
  }

  setModel(model) {
    const formattedModel = model === 'slim' ? 'slim' : 'default';
    this.currentModel = formattedModel;
    if (this.currentSkinUrl) {
      this.viewer.loadSkin(this.currentSkinUrl, { model: formattedModel });
    }
  }

  setAnimation(type) {
    this.viewer.animations.reset();
    if (type === 'walk') {
      this.animation = this.viewer.animations.add(skinview3d.WalkingAnimation);
      this.animation.speed = 0.8;
    } else if (type === 'run') {
      this.animation = this.viewer.animations.add(skinview3d.RunningAnimation);
      this.animation.speed = 0.8;
    } else if (type === 'wave') {
      this.animation = this.viewer.animations.add(skinview3d.WaveAnimation);
      this.animation.speed = 0.8;
    } else if (type === 'none') {
      this.animation = null;
    } else {
      this.animation = this.viewer.animations.add(skinview3d.IdleAnimation);
      this.animation.speed = 0.8;
    }
  }

  resetCamera() {
    this.viewer.camera.position.x = 0;
    this.viewer.camera.position.y = 5;
    this.viewer.camera.position.z = 60;
    this.viewer.camera.lookAt(0, 0, 0);
  }

  dispose() {
    this.viewer.dispose();
  }
}
