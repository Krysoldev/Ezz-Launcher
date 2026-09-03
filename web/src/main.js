import {
  getSupabase,
  getCurrentUser,
  getMinecraftProfile,
  createMinecraftProfile,
  uploadSkinFile,
  getUserSkins,
  setActiveSkin,
  renameSkin,
  deleteSkin,
  resetPasswordForEmail,
  updatePassword,
  deleteUserAccount,
  validatePngSkin
} from './supabase.js';
import { SkinStudioViewer, PRESET_SKINS } from './skinStudio.js';

let currentViewer = null;
let currentSelectedSkinUrl = PRESET_SKINS[0].url;
let currentSelectedModel = 'default';
let uploadedFileBlob = null;

// ==========================================
// Toast Notification Utility
// ==========================================
export function showToast(message, isError = false) {
  const container = document.getElementById('toast-container');
  if (!container) return;

  const toast = document.createElement('div');
  toast.className = `toast ${isError ? 'error' : ''}`;
  toast.innerHTML = `
    <span>${isError ? '⚠️' : '✓'}</span>
    <div>${message}</div>
  `;
  container.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(100%)';
    toast.style.transition = 'all 0.3s ease';
    setTimeout(() => toast.remove(), 300);
  }, 3500);
}

// Check if Supabase is connected to a live URL
export function isSupabaseConfigured() {
  const url = localStorage.getItem('ezz_supabase_url');
  return url && url !== 'https://api.ezzlauncher.com' && !url.includes('placeholder');
}
async function renderPage() {
  const hash = (window.location.hash || '#home').replace('#', '').split('?')[0];
  const main = document.getElementById('main-content');
  const user = await getCurrentUser();
  const profile = user ? await getMinecraftProfile(user.id) : null;

  updateNavState(hash, user);

  if (currentViewer) {
    currentViewer.dispose();
    currentViewer = null;
  }

  switch (hash) {
    case 'features':
      renderFeaturesView(main);
      break;
    case 'skins':
      renderSkinStudioView(main, profile);
      break;
    case 'skins/library':
      if (!user) {
        window.location.hash = '#login';
        return;
      }
      renderSkinLibraryView(main, profile);
      break;
    case 'dashboard':
      if (!user) {
        window.location.hash = '#login';
        return;
      }
      renderDashboardView(main, user, profile);
      break;
    case 'minecraft':
    case 'minecraft/profile':
      if (!user) {
        window.location.hash = '#login';
        return;
      }
      renderProfileSetupView(main, user, profile);
      break;
    case 'account':
    case 'settings':
      if (!user) {
        window.location.hash = '#login';
        return;
      }
      renderSettingsView(main, user, profile);
      break;
    case 'download':
      renderDownloadView(main);
      break;
    case 'forgot-password':
      renderForgotPasswordView(main);
      break;
    case 'reset-password':
      renderResetPasswordView(main);
      break;
    case 'login':
      if (user) {
        window.location.hash = '#dashboard';
        return;
      }
      renderLoginView(main);
      break;
    case 'register':
      if (user) {
        window.location.hash = '#dashboard';
        return;
      }
      renderRegisterView(main);
      break;
    case 'home':
    default:
      renderHomeView(main, user, profile);
      break;
  }
}

function updateNavState(currentRoute, user) {
  document.querySelectorAll('.nav-link').forEach(link => {
    link.classList.toggle('active', link.dataset.page === currentRoute);
  });

  const actions = document.getElementById('nav-actions');
  if (!actions) return;

  if (user) {
    actions.innerHTML = `
      <a href="#dashboard" class="btn btn-secondary btn-sm">Dashboard</a>
      <a href="#settings" class="btn btn-secondary btn-sm">Settings</a>
      <button id="logout-btn" class="btn btn-danger btn-sm">Sign Out</button>
    `;
    document.getElementById('logout-btn')?.addEventListener('click', async () => {
      await getSupabase().auth.signOut();
      showToast('Signed out successfully');
      window.location.hash = '#home';
      renderPage();
    });
  } else {
    actions.innerHTML = `
      <button id="nav-connect-sb-btn" class="btn btn-secondary btn-sm" title="Configure Supabase Database">⚙️ Database</button>
      <a href="#login" class="btn btn-secondary btn-sm">Sign In</a>
      <a href="#register" class="btn btn-primary btn-sm">Create Account</a>
    `;
    document.getElementById('nav-connect-sb-btn')?.addEventListener('click', () => {
      showSupabaseConfigModal();
    });
  }
}

// ==========================================
// HOME VIEW
// ==========================================
function renderHomeView(container, user, profile) {
  container.innerHTML = `
    <section class="hero">
      <div class="hero-left">
        <div class="hero-pill">
          <span>⚡</span> Serverless Ezz Platform 2026
        </div>
        <h1 class="hero-title">
          Play Minecraft With <span class="gradient-text">Unified Profiles</span> & Cloud Skins.
        </h1>
        <p class="hero-desc">
          Create your permanent Minecraft username, manage HD skins with 3D pixel-perfect rendering, and auto-sync with the high-performance Ezz Launcher on any device.
        </p>
        <div class="hero-actions">
          ${
            user
              ? `<a href="#dashboard" class="btn btn-primary btn-lg">Go to Dashboard</a>`
              : `<a href="#register" class="btn btn-primary btn-lg">Create Ezz Account</a>`
          }
          <a href="#skins" class="btn btn-secondary btn-lg">Open 3D Skin Studio</a>
        </div>
      </div>

      <div class="hero-preview">
        <canvas id="hero-skin-canvas"></canvas>
      </div>
    </section>

    <section class="features-section">
      <div class="section-header">
        <h2>Built For Next-Gen Minecraft Players</h2>
        <p>100% Serverless. Zero VPS requirement. Zero custom client mods.</p>
      </div>

      <div class="features-grid">
        <div class="card feature-card">
          <div class="feature-icon">🛡️</div>
          <h3>Centralized Cloud Identity</h3>
          <p>Register your unique Minecraft profile on the web. It receives a permanent UUID and stays synced forever without duplicates.</p>
        </div>
        <div class="card feature-card">
          <div class="feature-icon">✨</div>
          <h3>Pixel-Perfect 3D Skin Studio</h3>
          <p>Upload 64x64 PNG skins, toggle classic Steve vs slim Alex arm models, test walking animations, and rotate with natural controls.</p>
        </div>
        <div class="card feature-card">
          <div class="feature-icon">⚡</div>
          <h3>Launcher Auto-Detection</h3>
          <p>Simply enter your username in EzzLauncher. It auto-detects your Ezz profile and UUID without entering passwords in the launcher.</p>
        </div>
      </div>
    </section>
  `;

  const canvas = document.getElementById('hero-skin-canvas');
  if (canvas) {
    currentViewer = new SkinStudioViewer(canvas, { width: 340, height: 440 });
  }
}

// ==========================================
// FEATURES VIEW
// ==========================================
function renderFeaturesView(container) {
  container.innerHTML = `
    <div style="max-width: 1000px; margin: 40px auto; padding: 0 24px;">
      <div class="section-header">
        <h2>Architecture & Technology</h2>
        <p>VPS-less serverless infrastructure powered by Cloudflare and Supabase.</p>
      </div>

      <div class="card" style="margin-bottom: 24px;">
        <h3 style="margin-bottom: 12px; color: var(--accent-green);">1. Website Account & Profile Ownership</h3>
        <p style="color: var(--text-secondary); line-height: 1.7;">
          You create your Ezz website account securely using Supabase Auth. Your Minecraft Profile is assigned a permanent, stable UUID. Your skin is stored immutably in Supabase Storage with SHA-256 validation.
        </p>
      </div>

      <div class="card" style="margin-bottom: 24px;">
        <h3 style="margin-bottom: 12px; color: var(--accent-cyan);">2. Clean Launcher Auto-Discovery</h3>
        <p style="color: var(--text-secondary); line-height: 1.7;">
          Inside EzzLauncher, you never need to type website passwords. Clicking "Add Offline Account" and typing your registered Minecraft username performs a lightweight lookup against our privacy-safe public endpoint, binding your cloud profile instantly.
        </p>
      </div>

      <div class="card">
        <h3 style="margin-bottom: 12px; color: #fff;">3. Safe Multiplayer Isolation</h3>
        <p style="color: var(--text-secondary); line-height: 1.7;">
          Minecraft launches cleanly using standard JVM arguments and session parameters. Other players on multiplayer servers keep their own skins without any global client overrides.
        </p>
      </div>
    </div>
  `;
}

// ==========================================
// SKIN STUDIO VIEW
// ==========================================
function renderSkinStudioView(container, profile) {
  container.innerHTML = `
    <div class="studio-container">
      <div class="viewer-box">
        <canvas id="studio-canvas"></canvas>
        <div class="viewer-controls">
          <button class="ctrl-btn active" data-anim="idle">Idle</button>
          <button class="ctrl-btn" data-anim="walk">Walk</button>
          <button class="ctrl-btn" data-anim="run">Run</button>
          <button class="ctrl-btn" data-anim="wave">Wave</button>
          <button class="ctrl-btn" id="reset-cam-btn">Reset View</button>
        </div>
      </div>

      <div class="card">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
          <h2>3D Skin Studio</h2>
          ${profile ? `<a href="#skins/library" class="btn btn-secondary btn-sm">My Skins Library</a>` : ''}
        </div>
        <p style="color: var(--text-secondary); margin-bottom: 20px; font-size: 0.95rem;">
          Upload or choose a skin. Drag to rotate model. Mouse scroll to zoom.
        </p>

        <div class="form-group">
          <label class="form-label">Arm Model Style</label>
          <div style="display: flex; gap: 12px;">
            <button id="model-classic-btn" class="btn btn-secondary ${currentSelectedModel === 'default' ? 'btn-primary' : ''} btn-sm" style="flex: 1;">
              Classic (Steve 4px)
            </button>
            <button id="model-slim-btn" class="btn btn-secondary ${currentSelectedModel === 'slim' ? 'btn-primary' : ''} btn-sm" style="flex: 1;">
              Slim (Alex 3px)
            </button>
          </div>
        </div>

        <div class="form-group">
          <label class="form-label">Upload Custom Skin (64x64 PNG)</label>
          <div id="skin-dropzone" class="dropzone">
            <div style="font-size: 2rem; margin-bottom: 8px;">📁</div>
            <p style="font-weight: 600;">Drag & drop your skin PNG here</p>
            <p style="font-size: 0.85rem; color: var(--text-muted);">or click to browse from device (64x64 or 64x32)</p>
            <input type="file" id="skin-file-input" accept="image/png" style="display: none;" />
          </div>
        </div>

        <div class="form-group">
          <label class="form-label">Skin Name</label>
          <input type="text" id="skin-name-input" class="form-input" placeholder="e.g. My Favorite Outfit" />
        </div>

        <div class="form-group">
          <label class="form-label">Preset Skins Gallery</label>
          <div class="skin-presets">
            ${PRESET_SKINS.map(
              preset => `
              <div class="preset-thumb ${preset.url === currentSelectedSkinUrl ? 'active' : ''}" data-url="${preset.url}" data-model="${preset.model}" data-name="${preset.name}">
                <img src="${preset.url}" alt="${preset.name}" />
                <div class="preset-name">${preset.name}</div>
              </div>
            `
            ).join('')}
          </div>
        </div>

        ${
          profile
            ? `<button id="apply-skin-btn" class="btn btn-primary btn-lg" style="width: 100%; margin-top: 16px;">
                Save & Set as Active Skin for ${profile.username}
              </button>`
            : `<a href="#login" class="btn btn-secondary btn-lg" style="width: 100%; margin-top: 16px;">
                Sign In to Save Skin to Profile
              </a>`
        }
      </div>
    </div>
  `;

  // Init Studio Viewer
  const canvas = document.getElementById('studio-canvas');
  if (canvas) {
    currentViewer = new SkinStudioViewer(canvas, {
      width: 440,
      height: 520,
      skin: currentSelectedSkinUrl,
      model: currentSelectedModel
    });
  }

  // Animation Controls
  document.querySelectorAll('.viewer-controls .ctrl-btn').forEach(btn => {
    if (btn.id === 'reset-cam-btn') return;
    btn.addEventListener('click', () => {
      document.querySelectorAll('.viewer-controls .ctrl-btn').forEach(b => {
        if (b.id !== 'reset-cam-btn') b.classList.remove('active');
      });
      btn.classList.add('active');
      currentViewer?.setAnimation(btn.dataset.anim);
    });
  });

  // Reset Camera
  document.getElementById('reset-cam-btn')?.addEventListener('click', () => {
    currentViewer?.resetCamera();
    showToast('Camera reset to default');
  });

  // Model selection
  const classicBtn = document.getElementById('model-classic-btn');
  const slimBtn = document.getElementById('model-slim-btn');
  classicBtn?.addEventListener('click', () => {
    currentSelectedModel = 'default';
    classicBtn.className = 'btn btn-primary btn-sm';
    if (slimBtn) slimBtn.className = 'btn btn-secondary btn-sm';
    currentViewer?.setModel('default');
  });
  slimBtn?.addEventListener('click', () => {
    currentSelectedModel = 'slim';
    slimBtn.className = 'btn btn-primary btn-sm';
    if (classicBtn) classicBtn.className = 'btn btn-secondary btn-sm';
    currentViewer?.setModel('slim');
  });

  // Presets click
  document.querySelectorAll('.preset-thumb').forEach(thumb => {
    thumb.addEventListener('click', () => {
      document.querySelectorAll('.preset-thumb').forEach(t => t.classList.remove('active'));
      thumb.classList.add('active');
      currentSelectedSkinUrl = thumb.dataset.url;
      currentSelectedModel = thumb.dataset.model;
      uploadedFileBlob = null;
      document.getElementById('skin-name-input').value = thumb.dataset.name;
      currentViewer?.loadSkin(currentSelectedSkinUrl, currentSelectedModel);
    });
  });

  // File Upload Handling
  const dropzone = document.getElementById('skin-dropzone');
  const fileInput = document.getElementById('skin-file-input');
  dropzone?.addEventListener('click', () => fileInput?.click());

  fileInput?.addEventListener('change', e => {
    const file = e.target.files[0];
    if (file) handleSkinFile(file);
  });

  dropzone?.addEventListener('dragover', e => {
    e.preventDefault();
    dropzone.classList.add('dragover');
  });
  dropzone?.addEventListener('dragleave', () => dropzone.classList.remove('dragover'));
  dropzone?.addEventListener('drop', e => {
    e.preventDefault();
    dropzone.classList.remove('dragover');
    if (e.dataTransfer.files[0]) handleSkinFile(e.dataTransfer.files[0]);
  });

  async function handleSkinFile(file) {
    try {
      const validation = await validatePngSkin(file);
      const url = URL.createObjectURL(validation.blob);
      currentSelectedSkinUrl = url;
      uploadedFileBlob = file;
      document.getElementById('skin-name-input').value = file.name.replace(/\.[^/.]+$/, '');
      currentViewer?.loadSkin(url, currentSelectedModel);
      showToast('Valid 64x64 PNG loaded into 3D viewer!');
    } catch (err) {
      showToast(err.message, true);
    }
  }

  // Save Skin Action
  document.getElementById('apply-skin-btn')?.addEventListener('click', async () => {
    if (!profile) return;
    const btn = document.getElementById('apply-skin-btn');
    const skinName = document.getElementById('skin-name-input').value || 'Custom Skin';
    const model = currentSelectedModel === 'slim' ? 'ALEX' : 'STEVE';

    btn.disabled = true;
    btn.textContent = 'Saving Skin...';

    try {
      if (uploadedFileBlob) {
        await uploadSkinFile(profile, uploadedFileBlob, skinName, model);
      } else {
        // Fallback for preset
        await updateProfileSkin(profile.id, currentSelectedSkinUrl, model);
      }
      showToast('Skin saved and set as active!');
      setTimeout(() => {
        window.location.hash = '#dashboard';
        renderPage();
      }, 500);
    } catch (err) {
      showToast(err.message, true);
      btn.disabled = false;
      btn.textContent = 'Save & Set as Active Skin';
    }
  });
}

// ==========================================
// SKIN LIBRARY VIEW
// ==========================================
async function renderSkinLibraryView(container, profile) {
  container.innerHTML = `
    <div style="max-width: 1200px; margin: 40px auto; padding: 0 24px;">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 28px;">
        <div>
          <h2>My Skins Library</h2>
          <p style="color: var(--text-secondary);">Manage all skins associated with Minecraft profile '${profile ? profile.username : ''}'</p>
        </div>
        <a href="#skins" class="btn btn-primary">+ Upload New Skin</a>
      </div>

      <div id="skins-grid" class="features-grid">
        <div class="card" style="text-align: center; padding: 40px;">
          <p style="color: var(--text-secondary);">Loading your skin wardrobe...</p>
        </div>
      </div>
    </div>
  `;

  if (!profile) return;

  try {
    const skins = await getUserSkins(profile.id);
    const grid = document.getElementById('skins-grid');

    if (!skins || skins.length === 0) {
      grid.innerHTML = `
        <div class="card" style="text-align: center; padding: 50px; grid-column: 1 / -1;">
          <h3>No Skins in Library</h3>
          <p style="color: var(--text-secondary); margin: 12px 0 24px;">You haven't uploaded any custom skins yet.</p>
          <a href="#skins" class="btn btn-primary">Open 3D Skin Studio & Upload</a>
        </div>
      `;
      return;
    }

    grid.innerHTML = skins
      .map(skin => {
        const isActive = profile.skin_id === skin.id;
        const textureUrl = `https://api.ezzlauncher.com/storage/v1/object/public/minecraft-skins/${skin.storage_path}`;
        return `
        <div class="card feature-card" style="position: relative;">
          <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px;">
            <span class="badge ${isActive ? 'badge-green' : 'badge-neutral'}">${isActive ? 'Active Skin' : 'Saved'}</span>
            <span class="badge badge-cyan">${skin.model || 'STEVE'}</span>
          </div>

          <div style="display: flex; align-items: center; gap: 16px; margin-bottom: 20px;">
            <img src="${textureUrl}" alt="${skin.name}" style="width: 56px; height: 56px; image-rendering: pixelated; border-radius: 8px; border: 1px solid var(--border-subtle);" onerror="this.src='${PRESET_SKINS[0].url}'" />
            <div>
              <h3 style="font-size: 1.15rem; margin-bottom: 4px;">${skin.name}</h3>
              <p style="font-size: 0.8rem; color: var(--text-muted);">${skin.width}x${skin.height} • ${(skin.file_size / 1024).toFixed(1)} KB</p>
            </div>
          </div>

          <div style="display: flex; gap: 8px;">
            ${
              !isActive
                ? `<button class="btn btn-primary btn-sm set-active-btn" data-id="${skin.id}" data-model="${skin.model}" style="flex: 1;">Set Active</button>`
                : `<button class="btn btn-secondary btn-sm" disabled style="flex: 1; opacity: 0.6;">Active</button>`
            }
            <button class="btn btn-secondary btn-sm rename-btn" data-id="${skin.id}" data-name="${skin.name}">Rename</button>
            <button class="btn btn-danger btn-sm delete-btn" data-id="${skin.id}" ${isActive ? 'disabled style="opacity: 0.4;"' : ''}>Delete</button>
          </div>
        </div>
      `;
      })
      .join('');

    // Event listeners
    document.querySelectorAll('.set-active-btn').forEach(btn => {
      btn.addEventListener('click', async () => {
        try {
          await setActiveSkin(profile.id, btn.dataset.id, btn.dataset.model);
          showToast('Active skin updated!');
          renderPage();
        } catch (err) {
          showToast(err.message, true);
        }
      });
    });

    document.querySelectorAll('.rename-btn').forEach(btn => {
      btn.addEventListener('click', async () => {
        const newName = prompt('Enter new skin name:', btn.dataset.name);
        if (newName && newName.trim()) {
          try {
            await renameSkin(btn.dataset.id, newName.trim());
            showToast('Skin renamed!');
            renderPage();
          } catch (err) {
            showToast(err.message, true);
          }
        }
      });
    });

    document.querySelectorAll('.delete-btn').forEach(btn => {
      btn.addEventListener('click', async () => {
        if (confirm('Are you sure you want to delete this skin?')) {
          try {
            await deleteSkin(btn.dataset.id, profile);
            showToast('Skin deleted');
            renderPage();
          } catch (err) {
            showToast(err.message, true);
          }
        }
      });
    });
  } catch (err) {
    document.getElementById('skins-grid').innerHTML = `
      <div class="card" style="text-align: center; padding: 40px; color: var(--danger);">
        Error loading skins: ${err.message}
      </div>
    `;
  }
}

// ==========================================
// DASHBOARD VIEW
// ==========================================
function renderDashboardView(container, user, profile) {
  container.innerHTML = `
    <div class="dashboard-grid">
      <!-- Profile Card -->
      <div class="card">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
          <h2>Minecraft Profile</h2>
          <span class="badge badge-green">Ezz Profile</span>
        </div>

        ${
          profile
            ? `
          <div style="margin-bottom: 20px;">
            <div style="font-size: 0.85rem; color: var(--text-secondary); text-transform: uppercase; font-weight: 700;">Username</div>
            <div style="font-size: 1.6rem; font-weight: 800; color: #fff;">${profile.username}</div>
          </div>

          <div style="margin-bottom: 20px;">
            <div style="font-size: 0.85rem; color: var(--text-secondary); text-transform: uppercase; font-weight: 700;">Permanent Stable UUID</div>
            <div class="uuid-box">
              <span>${profile.uuid}</span>
              <button class="copy-btn" id="copy-uuid-btn">Copy</button>
            </div>
          </div>

          <div style="margin-bottom: 20px;">
            <div style="font-size: 0.85rem; color: var(--text-secondary); text-transform: uppercase; font-weight: 700;">Skin Model</div>
            <div style="font-size: 1.1rem; color: var(--text-primary); font-weight: 600;">
              ${profile.skin_model === 'ALEX' ? 'Slim (Alex 3px)' : 'Classic (Steve 4px)'}
            </div>
          </div>

          <div style="display: flex; gap: 12px;">
            <a href="#skins" class="btn btn-primary" style="flex: 1;">3D Skin Studio</a>
            <a href="#skins/library" class="btn btn-secondary" style="flex: 1;">My Wardrobe</a>
          </div>
        `
            : `
          <div style="padding: 20px 0; text-align: center;">
            <p style="color: var(--text-secondary); margin-bottom: 20px;">You have not created a Minecraft profile yet.</p>
            <a href="#minecraft/profile" class="btn btn-primary btn-lg" style="width: 100%;">Create Minecraft Profile</a>
          </div>
        `
        }
      </div>

      <!-- Account Info & Launcher Integration -->
      <div class="card">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
          <h2>Ezz Account</h2>
          <span class="badge badge-cyan">Active</span>
        </div>

        <div style="margin-bottom: 24px;">
          <div style="font-size: 0.85rem; color: var(--text-secondary); text-transform: uppercase; font-weight: 700;">Email Address</div>
          <div style="font-size: 1.1rem; color: #fff; font-weight: 600;">${user.email}</div>
        </div>

        <div class="card" style="background: rgba(0, 240, 118, 0.05); border-color: rgba(0, 240, 118, 0.2); margin-bottom: 24px;">
          <h3 style="font-size: 1.1rem; margin-bottom: 8px; color: var(--accent-green);">How to use in EzzLauncher</h3>
          <ol style="margin-left: 20px; color: var(--text-secondary); font-size: 0.95rem; line-height: 1.7;">
            <li>Open Ezz Launcher on your desktop.</li>
            <li>Go to <strong>Accounts</strong> and click <strong>Add Offline Account</strong>.</li>
            <li>Enter your registered username: <strong>${profile ? profile.username : '(Create profile first)'}</strong>.</li>
            <li>The launcher automatically links your cloud skin and permanent UUID!</li>
          </ol>
        </div>

        <div style="display: flex; justify-content: space-between; align-items: center; border-top: 1px solid var(--border-subtle); padding-top: 20px;">
          <span style="font-size: 0.85rem; color: var(--text-muted);">Domain: ezzlauncher.dpdns.org</span>
          <a href="#settings" class="btn btn-secondary btn-sm">Account Settings</a>
        </div>
      </div>
    </div>
  `;

  document.getElementById('copy-uuid-btn')?.addEventListener('click', () => {
    if (profile?.uuid) {
      navigator.clipboard.writeText(profile.uuid);
      showToast('UUID copied to clipboard!');
    }
  });
}

// ==========================================
// PROFILE SETUP VIEW
// ==========================================
function renderProfileSetupView(container, user, existingProfile) {
  if (existingProfile) {
    window.location.hash = '#dashboard';
    return;
  }

  container.innerHTML = `
    <div class="auth-container">
      <div class="card auth-card">
        <div class="auth-header">
          <h2>Create Minecraft Profile</h2>
          <p>Choose your permanent Minecraft username</p>
        </div>

        <form id="profile-form">
          <div class="form-group">
            <label class="form-label">Minecraft Username</label>
            <input type="text" id="prof-username" class="form-input" placeholder="e.g. KrysolDev" required minlength="3" maxlength="16" />
            <small style="color: var(--text-muted); display: block; margin-top: 6px; font-size: 0.8rem;">
              3-16 alphanumeric characters. Unique across Ezz network.
            </small>
          </div>

          <div class="form-group">
            <label class="form-label">Arm Model Style</label>
            <select id="prof-model" class="form-input" style="cursor: pointer;">
              <option value="STEVE">Classic (Steve 4px)</option>
              <option value="ALEX">Slim (Alex 3px)</option>
            </select>
          </div>

          <button type="submit" id="create-prof-btn" class="btn btn-primary btn-lg" style="width: 100%; margin-top: 10px;">
            Create Profile & Generate UUID
          </button>
        </form>
      </div>
    </div>
  `;

  document.getElementById('profile-form')?.addEventListener('submit', async e => {
    e.preventDefault();
    const username = document.getElementById('prof-username').value;
    const model = document.getElementById('prof-model').value;
    const btn = document.getElementById('create-prof-btn');

    btn.disabled = true;
    btn.textContent = 'Generating Profile...';

    try {
      await createMinecraftProfile(username, model);
      showToast(`Minecraft Profile '${username}' created!`);
      window.location.hash = '#dashboard';
      renderPage();
    } catch (err) {
      showToast(err.message, true);
      btn.disabled = false;
      btn.textContent = 'Create Profile & Generate UUID';
    }
  });
}

// ==========================================
// SETTINGS VIEW
// ==========================================
function renderSettingsView(container, user, profile) {
  container.innerHTML = `
    <div style="max-width: 800px; margin: 40px auto; padding: 0 24px;">
      <div class="section-header" style="text-align: left; margin-bottom: 30px;">
        <h2>Account Settings</h2>
        <p>Manage your credentials and security</p>
      </div>

      <div class="card" style="margin-bottom: 24px;">
        <h3 style="margin-bottom: 16px;">Change Password</h3>
        <form id="change-pass-form">
          <div class="form-group">
            <label class="form-label">New Password</label>
            <input type="password" id="new-password" class="form-input" placeholder="••••••••" required minlength="6" />
          </div>
          <button type="submit" class="btn btn-primary">Update Password</button>
        </form>
      </div>

      <div class="card" style="border-color: rgba(255, 71, 87, 0.3);">
        <h3 style="color: var(--danger); margin-bottom: 8px;">Delete Account</h3>
        <p style="color: var(--text-secondary); margin-bottom: 20px; font-size: 0.9rem;">
          Permanently deletes your Ezz account, Minecraft profile, UUID, and all uploaded skins.
        </p>
        <button id="delete-acc-btn" class="btn btn-danger">Delete Ezz Account</button>
      </div>
    </div>
  `;

  document.getElementById('change-pass-form')?.addEventListener('submit', async e => {
    e.preventDefault();
    const newPass = document.getElementById('new-password').value;
    try {
      await updatePassword(newPass);
      showToast('Password updated successfully!');
      document.getElementById('new-password').value = '';
    } catch (err) {
      showToast(err.message, true);
    }
  });

  document.getElementById('delete-acc-btn')?.addEventListener('click', async () => {
    if (confirm('WARNING: This will permanently delete your account and Minecraft profile. Proceed?')) {
      try {
        await deleteUserAccount();
        showToast('Account deleted');
        window.location.hash = '#home';
        renderPage();
      } catch (err) {
        showToast(err.message, true);
      }
    }
  });
}

// ==========================================
// DOWNLOAD VIEW
// ==========================================
function renderDownloadView(container) {
  container.innerHTML = `
    <div style="max-width: 900px; margin: 60px auto; padding: 0 24px; text-align: center;">
      <h1 style="font-size: 3rem; margin-bottom: 16px;">Download Ezz Launcher</h1>
      <p style="color: var(--text-secondary); font-size: 1.15rem; max-width: 600px; margin: 0 auto 40px;">
        Fast, lightweight, native Minecraft launcher with unified cloud skins and zero client mods.
      </p>

      <div style="display: flex; justify-content: center; gap: 20px; flex-wrap: wrap;">
        <div class="card" style="width: 260px; text-align: center; padding: 30px;">
          <div style="font-size: 2.5rem; margin-bottom: 12px;">🪟</div>
          <h3>Windows</h3>
          <p style="color: var(--text-secondary); font-size: 0.85rem; margin: 8px 0 20px;">Windows 10 / 11 (64-bit)</p>
          <a href="https://ezzlauncher.dpdns.org" class="btn btn-primary" style="width: 100%;">Download .exe</a>
        </div>
        <div class="card" style="width: 260px; text-align: center; padding: 30px;">
          <div style="font-size: 2.5rem; margin-bottom: 12px;">🍎</div>
          <h3>macOS</h3>
          <p style="color: var(--text-secondary); font-size: 0.85rem; margin: 8px 0 20px;">Intel & Apple Silicon</p>
          <a href="https://ezzlauncher.dpdns.org" class="btn btn-secondary" style="width: 100%;">Download .dmg</a>
        </div>
        <div class="card" style="width: 260px; text-align: center; padding: 30px;">
          <div style="font-size: 2.5rem; margin-bottom: 12px;">🐧</div>
          <h3>Linux</h3>
          <p style="color: var(--text-secondary); font-size: 0.85rem; margin: 8px 0 20px;">AppImage / Tar.gz</p>
          <a href="https://ezzlauncher.dpdns.org" class="btn btn-secondary" style="width: 100%;">Download .AppImage</a>
        </div>
      </div>
    </div>
  `;
}

// ==========================================
// LOGIN & REGISTER VIEWS
// ==========================================
function renderLoginView(container) {
  container.innerHTML = `
    <div class="auth-container">
      <div class="card auth-card">
        <div class="auth-header">
          <h2>Sign In to Ezz</h2>
          <p>Access your cloud profile & skins</p>
        </div>

        <form id="login-form">
          <div class="form-group">
            <label class="form-label">Email Address</label>
            <input type="email" id="login-email" class="form-input" placeholder="player@example.com" required />
          </div>

          <div class="form-group">
            <div style="display: flex; justify-content: space-between;">
              <label class="form-label">Password</label>
              <a href="#forgot-password" style="font-size: 0.8rem; color: var(--accent-green);">Forgot?</a>
            </div>
            <input type="password" id="login-password" class="form-input" placeholder="••••••••" required />
          </div>

          <button type="submit" id="login-submit-btn" class="btn btn-primary btn-lg" style="width: 100%; margin-top: 10px;">
            Sign In
          </button>
        </form>

        <div class="auth-footer">
          Don't have an account? <a href="#register">Create Account</a>
        </div>
      </div>
    </div>
  `;

  document.getElementById('login-form')?.addEventListener('submit', async e => {
    e.preventDefault();
    const email = document.getElementById('login-email').value;
    const password = document.getElementById('login-password').value;
    const btn = document.getElementById('login-submit-btn');

    btn.disabled = true;
    btn.textContent = 'Signing in...';

    try {
      const { error } = await getSupabase().auth.signInWithPassword({ email, password });
      if (error) throw error;
      showToast('Welcome back!');
      window.location.hash = '#dashboard';
      renderPage();
    } catch (err) {
      showToast(err.message, true);
      btn.disabled = false;
      btn.textContent = 'Sign In';
    }
  });
}

function renderRegisterView(container) {
  container.innerHTML = `
    <div class="auth-container">
      <div class="card auth-card">
        <div class="auth-header">
          <h2>Create Ezz Account</h2>
          <p>Join the decentralized Minecraft platform</p>
        </div>

        <form id="register-form">
          <div class="form-group">
            <label class="form-label">Email Address</label>
            <input type="email" id="reg-email" class="form-input" placeholder="player@example.com" required />
          </div>

          <div class="form-group">
            <label class="form-label">Password</label>
            <div style="position: relative;">
              <input type="password" id="reg-password" class="form-input" placeholder="••••••••" required minlength="6" />
              <button type="button" class="pass-toggle-btn" data-target="reg-password" style="position: absolute; right: 12px; top: 50%; transform: translateY(-50%); background: transparent; border: none; color: var(--text-muted); cursor: pointer; font-size: 1rem;">👁️</button>
            </div>
          </div>

          <div class="form-group">
            <label class="form-label">Confirm Password</label>
            <div style="position: relative;">
              <input type="password" id="reg-confirm" class="form-input" placeholder="••••••••" required minlength="6" />
              <button type="button" class="pass-toggle-btn" data-target="reg-confirm" style="position: absolute; right: 12px; top: 50%; transform: translateY(-50%); background: transparent; border: none; color: var(--text-muted); cursor: pointer; font-size: 1rem;">👁️</button>
            </div>
          </div>

          <button type="submit" id="reg-submit-btn" class="btn btn-primary btn-lg" style="width: 100%; margin-top: 10px;">
            Create Account
          </button>
        </form>

        <div class="auth-footer">
          Already have an account? <a href="#login">Sign In</a>
        </div>
      </div>
    </div>
  `;

  document.querySelectorAll('.pass-toggle-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const input = document.getElementById(btn.dataset.target);
      if (input) {
        input.type = input.type === 'password' ? 'text' : 'password';
      }
    });
  });

  document.getElementById('register-form')?.addEventListener('submit', async e => {
    e.preventDefault();
    const email = document.getElementById('reg-email').value;
    const password = document.getElementById('reg-password').value;
    const confirm = document.getElementById('reg-confirm').value;
    const btn = document.getElementById('reg-submit-btn');

    if (password !== confirm) {
      showToast('Passwords do not match', true);
      return;
    }

    btn.disabled = true;
    btn.textContent = 'Creating Account...';

    try {
      const { error } = await getSupabase().auth.signUp({ email, password });
      if (error) throw error;
      showToast('Account created successfully!');
      window.location.hash = '#minecraft/profile';
      renderPage();
    } catch (err) {
      if (err.message.includes('fetch') || err.message.includes('Network')) {
        showToast('Cannot reach Supabase. Click "Connect Supabase" below to enter your Supabase URL & Key.', true);
        showSupabaseConfigModal();
      } else {
        showToast(err.message, true);
      }
      btn.disabled = false;
      btn.textContent = 'Create Account';
    }
  });
}

export function showSupabaseConfigModal() {
  let modal = document.getElementById('supabase-config-modal');
  if (!modal) {
    modal = document.createElement('div');
    modal.id = 'supabase-config-modal';
    modal.innerHTML = `
      <div class="modal-backdrop" style="position: fixed; inset: 0; background: rgba(0,0,0,0.8); z-index: 1000; display: flex; align-items: center; justify-content: center; padding: 20px;">
        <div class="card" style="max-width: 500px; width: 100%; background: #0f1015; border: 1px solid var(--accent-green); box-shadow: 0 0 30px rgba(0, 240, 118, 0.2);">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
            <h3>Connect Supabase Backend</h3>
            <button id="close-modal-btn" style="background: transparent; border: none; color: #fff; font-size: 1.2rem; cursor: pointer;">✕</button>
          </div>
          <p style="color: var(--text-secondary); font-size: 0.9rem; margin-bottom: 20px; line-height: 1.6;">
            Enter your Supabase Project URL and Public Anon Key from your <a href="https://supabase.com/dashboard" target="_blank" style="color: var(--accent-green); text-decoration: underline;">Supabase Dashboard</a> (Settings → API).
          </p>
          <div class="form-group">
            <label class="form-label">Supabase Project URL</label>
            <input type="url" id="modal-sb-url" class="form-input" placeholder="https://your-project.supabase.co" value="${localStorage.getItem('ezz_supabase_url') || ''}" required />
          </div>
          <div class="form-group">
            <label class="form-label">Supabase Anon Key</label>
            <textarea id="modal-sb-key" class="form-input" rows="3" placeholder="eyJhbGciOi..." style="resize: none;" required>${localStorage.getItem('ezz_supabase_anon_key') || ''}</textarea>
          </div>
          <div style="display: flex; gap: 12px; margin-top: 24px;">
            <button id="save-sb-btn" class="btn btn-primary" style="flex: 1;">Save & Connect</button>
            <button id="cancel-sb-btn" class="btn btn-secondary">Cancel</button>
          </div>
        </div>
      </div>
    `;
    document.body.appendChild(modal);

    const closeModal = () => modal.remove();
    document.getElementById('close-modal-btn')?.addEventListener('click', closeModal);
    document.getElementById('cancel-sb-btn')?.addEventListener('click', closeModal);

    document.getElementById('save-sb-btn')?.addEventListener('click', () => {
      const url = document.getElementById('modal-sb-url').value.trim();
      const key = document.getElementById('modal-sb-key').value.trim();
      if (!url || !key) {
        showToast('Please enter both Supabase URL and Anon Key', true);
        return;
      }
      import('./supabase.js').then(m => {
        m.updateSupabaseConfig(url, key);
        showToast('Supabase configuration saved!');
        closeModal();
        renderPage();
      });
    });
  }
}

function renderForgotPasswordView(container) {
  container.innerHTML = `
    <div class="auth-container">
      <div class="card auth-card">
        <div class="auth-header">
          <h2>Reset Password</h2>
          <p>We'll send you an email link</p>
        </div>

        <form id="forgot-form">
          <div class="form-group">
            <label class="form-label">Email Address</label>
            <input type="email" id="forgot-email" class="form-input" placeholder="player@example.com" required />
          </div>

          <button type="submit" id="forgot-btn" class="btn btn-primary btn-lg" style="width: 100%; margin-top: 10px;">
            Send Reset Link
          </button>
        </form>

        <div class="auth-footer">
          Remember your password? <a href="#login">Sign In</a>
        </div>
      </div>
    </div>
  `;

  document.getElementById('forgot-form')?.addEventListener('submit', async e => {
    e.preventDefault();
    const email = document.getElementById('forgot-email').value;
    const btn = document.getElementById('forgot-btn');
    btn.disabled = true;
    btn.textContent = 'Sending...';

    try {
      await resetPasswordForEmail(email);
      showToast('Password reset link sent to your email!');
      window.location.hash = '#login';
      renderPage();
    } catch (err) {
      showToast(err.message, true);
      btn.disabled = false;
      btn.textContent = 'Send Reset Link';
    }
  });
}

function renderResetPasswordView(container) {
  container.innerHTML = `
    <div class="auth-container">
      <div class="card auth-card">
        <div class="auth-header">
          <h2>Set New Password</h2>
          <p>Enter your new secure password</p>
        </div>

        <form id="reset-form">
          <div class="form-group">
            <label class="form-label">New Password</label>
            <input type="password" id="reset-new-pass" class="form-input" placeholder="••••••••" required minlength="6" />
          </div>

          <button type="submit" id="reset-btn" class="btn btn-primary btn-lg" style="width: 100%; margin-top: 10px;">
            Save New Password
          </button>
        </form>
      </div>
    </div>
  `;

  document.getElementById('reset-form')?.addEventListener('submit', async e => {
    e.preventDefault();
    const newPass = document.getElementById('reset-new-pass').value;
    const btn = document.getElementById('reset-btn');
    btn.disabled = true;

    try {
      await updatePassword(newPass);
      showToast('Password updated! You can now sign in.');
      window.location.hash = '#dashboard';
      renderPage();
    } catch (err) {
      showToast(err.message, true);
      btn.disabled = false;
    }
  });
}

// Global initialization
window.addEventListener('hashchange', renderPage);
window.addEventListener('DOMContentLoaded', renderPage);
renderPage();
