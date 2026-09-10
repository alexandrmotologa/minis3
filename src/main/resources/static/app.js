document.addEventListener('DOMContentLoaded', () => {
  let activeBucket = null;

  const statRawBytes = document.getElementById('stat-raw-bytes');
  const statDiskBytes = document.getElementById('stat-disk-bytes');
  const statDedupRatio = document.getElementById('stat-dedup-ratio');
  const statSavedPercent = document.getElementById('stat-saved-percent');
  const statUniqueChunks = document.getElementById('stat-unique-chunks');

  const bucketListEl = document.getElementById('bucket-list');
  const currentBucketTitle = document.getElementById('current-bucket-title');
  const breadcrumbEl = document.getElementById('breadcrumb');
  const bucketActions = document.getElementById('bucket-actions');
  const dropzone = document.getElementById('dropzone');
  const fileInput = document.getElementById('file-input');
  const browseLink = document.getElementById('browse-link');
  const uploadProgress = document.getElementById('upload-progress');
  const progressFill = document.getElementById('progress-fill');
  const progressText = document.getElementById('progress-text');
  const objectsTbody = document.getElementById('objects-tbody');

  const btnNewBucket = document.getElementById('btn-new-bucket');
  const btnDeleteBucket = document.getElementById('btn-delete-bucket');
  const btnGc = document.getElementById('btn-gc');
  const toast = document.getElementById('toast');

  // Format bytes helper
  function formatBytes(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }

  function showToast(msg) {
    toast.textContent = msg;
    toast.style.display = 'block';
    setTimeout(() => {
      toast.style.display = 'none';
    }, 3500);
  }

  // Load telemetry stats
  async function loadStats() {
    try {
      const res = await fetch('/api/admin/stats');
      if (res.ok) {
        const data = await res.json();
        statRawBytes.textContent = formatBytes(data.totalRawBytes || 0);
        statDiskBytes.textContent = formatBytes(data.totalCompressedBytes || 0);
        statDedupRatio.textContent = (data.deduplicationRatio || 1.0).toFixed(2) + 'x';
        statSavedPercent.textContent = (data.spaceSavedPercent || 0.0).toFixed(1) + '%';
        statUniqueChunks.textContent = (data.totalUniqueChunks || 0) + ' unique chunks';
      }
    } catch (e) {
      console.error('Failed to load telemetry stats', e);
    }
  }

  // Load buckets
  async function loadBuckets() {
    try {
      const res = await fetch('/api/admin/buckets');
      if (res.ok) {
        const buckets = await res.json();
        bucketListEl.innerHTML = '';
        if (buckets.length === 0) {
          bucketListEl.innerHTML = '<li class="empty-item">No buckets created yet.</li>';
          return;
        }

        buckets.forEach(b => {
          const li = document.createElement('li');
          li.className = 'bucket-item' + (activeBucket === b.name ? ' active' : '');
          li.innerHTML = `
            <span>${b.name}</span>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9 18 15 12 9 6"></polyline>
            </svg>
          `;
          li.addEventListener('click', () => selectBucket(b.name));
          bucketListEl.appendChild(li);
        });

        if (activeBucket && !buckets.some(b => b.name === activeBucket)) {
          selectBucket(null);
        }
      }
    } catch (e) {
      bucketListEl.innerHTML = '<li class="empty-item">Error loading buckets.</li>';
    }
  }

  // Select bucket
  async function selectBucket(name) {
    activeBucket = name;
    document.querySelectorAll('.bucket-item').forEach(el => {
      el.classList.toggle('active', el.querySelector('span')?.textContent === name);
    });

    if (!name) {
      currentBucketTitle.textContent = 'Select a bucket';
      breadcrumbEl.textContent = '/';
      bucketActions.style.display = 'none';
      dropzone.style.display = 'none';
      objectsTbody.innerHTML = '<tr><td colspan="6" class="empty-state">Select a bucket from the sidebar to browse objects.</td></tr>';
      return;
    }

    currentBucketTitle.textContent = name;
    breadcrumbEl.textContent = 's3://' + name + '/';
    bucketActions.style.display = 'block';
    dropzone.style.display = 'block';
    await loadObjects(name);
  }

  // Load objects for active bucket
  async function loadObjects(bucketName) {
    objectsTbody.innerHTML = '<tr><td colspan="6" class="empty-state">Loading objects...</td></tr>';
    try {
      const res = await fetch(`/api/admin/buckets/${encodeURIComponent(bucketName)}/objects`);
      if (res.ok) {
        const objects = await res.json();
        if (objects.length === 0) {
          objectsTbody.innerHTML = '<tr><td colspan="6" class="empty-state">This bucket is empty. Drag and drop files above to upload.</td></tr>';
          return;
        }

        objectsTbody.innerHTML = '';
        objects.forEach(obj => {
          const tr = document.createElement('tr');
          const lastMod = new Date(obj.createdAt).toLocaleString();
          const cleanEtag = (obj.etag || '').replace(/"/g, '');
          tr.innerHTML = `
            <td><strong>${escapeHtml(obj.key)}</strong></td>
            <td>${escapeHtml(obj.contentType || 'application/octet-stream')}</td>
            <td>${formatBytes(obj.size)}</td>
            <td><code>${cleanEtag.substring(0, 12)}...</code></td>
            <td>${lastMod}</td>
            <td style="text-align: right;">
              <a href="/${encodeURIComponent(bucketName)}/${encodeURIComponent(obj.key)}" target="_blank" class="action-link">Download</a>
              <a href="#" class="action-link action-delete" data-key="${escapeHtml(obj.key)}">Delete</a>
            </td>
          `;
          tr.querySelector('.action-delete').addEventListener('click', (e) => {
            e.preventDefault();
            deleteObject(bucketName, obj.key);
          });
          objectsTbody.appendChild(tr);
        });
      }
    } catch (e) {
      objectsTbody.innerHTML = '<tr><td colspan="6" class="empty-state">Error loading objects.</td></tr>';
    }
  }

  function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  // Upload file
  function uploadFile(file) {
    if (!activeBucket) return;
    uploadProgress.style.display = 'block';
    progressFill.style.width = '0%';
    progressText.textContent = `Uploading ${file.name}...`;

    const xhr = new XMLHttpRequest();
    xhr.open('PUT', `/${encodeURIComponent(activeBucket)}/${encodeURIComponent(file.name)}`, true);
    if (file.type) {
      xhr.setRequestHeader('Content-Type', file.type);
    }

    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) {
        const pct = Math.round((e.loaded / e.total) * 100);
        progressFill.style.width = pct + '%';
        progressText.textContent = `Uploading ${file.name} (${pct}%)...`;
      }
    };

    xhr.onload = () => {
      uploadProgress.style.display = 'none';
      if (xhr.status >= 200 && xhr.status < 300) {
        showToast(`Uploaded ${file.name} successfully`);
        loadObjects(activeBucket);
        loadStats();
      } else {
        showToast(`Upload failed: HTTP ${xhr.status}`);
      }
    };

    xhr.onerror = () => {
      uploadProgress.style.display = 'none';
      showToast('Network error during upload');
    };

    xhr.send(file);
  }

  // Create bucket
  btnNewBucket.addEventListener('click', async () => {
    const name = prompt('Enter a new bucket name (3-63 lowercase alphanumeric characters):');
    if (!name) return;
    try {
      const res = await fetch(`/api/admin/buckets?name=${encodeURIComponent(name.trim())}`, { method: 'POST' });
      if (res.ok) {
        showToast(`Bucket '${name}' created`);
        await loadBuckets();
        selectBucket(name.trim());
        loadStats();
      } else {
        const err = await res.json();
        showToast(`Failed: ${err.message || 'Error creating bucket'}`);
      }
    } catch (e) {
      showToast('Error connecting to server');
    }
  });

  // Delete bucket
  btnDeleteBucket.addEventListener('click', async () => {
    if (!activeBucket) return;
    if (!confirm(`Are you sure you want to delete bucket '${activeBucket}'?`)) return;

    try {
      const res = await fetch(`/api/admin/buckets/${encodeURIComponent(activeBucket)}`, { method: 'DELETE' });
      if (res.ok) {
        showToast(`Bucket '${activeBucket}' deleted`);
        selectBucket(null);
        await loadBuckets();
        loadStats();
      } else {
        showToast('Cannot delete non-empty bucket.');
      }
    } catch (e) {
      showToast('Error deleting bucket');
    }
  });

  // Delete object
  async function deleteObject(bucket, key) {
    if (!confirm(`Delete object '${key}'?`)) return;
    try {
      const res = await fetch(`/api/admin/buckets/${encodeURIComponent(bucket)}/objects?key=${encodeURIComponent(key)}`, { method: 'DELETE' });
      if (res.ok) {
        showToast(`Deleted ${key}`);
        loadObjects(bucket);
        loadStats();
      } else {
        showToast('Failed to delete object');
      }
    } catch (e) {
      showToast('Error deleting object');
    }
  }

  // Vacuum GC
  btnGc.addEventListener('click', async () => {
    btnGc.disabled = true;
    btnGc.textContent = 'Vacuuming...';
    try {
      const res = await fetch('/api/admin/gc', { method: 'POST' });
      if (res.ok) {
        const data = await res.json();
        showToast(`Garbage Collection finished: ${data.reclaimedChunks} chunks reclaimed.`);
        loadStats();
      }
    } catch (e) {
      showToast('GC failed');
    } finally {
      btnGc.disabled = false;
      btnGc.innerHTML = `
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="3 6 5 6 21 6"></polyline>
          <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
        </svg>
        Vacuum GC
      `;
    }
  });

  // File dropzone events
  browseLink.addEventListener('click', (e) => {
    e.preventDefault();
    fileInput.click();
  });

  fileInput.addEventListener('change', (e) => {
    Array.from(e.target.files).forEach(uploadFile);
    fileInput.value = '';
  });

  ['dragenter', 'dragover'].forEach(name => {
    dropzone.addEventListener(name, (e) => {
      e.preventDefault();
      dropzone.classList.add('drag-over');
    });
  });

  ['dragleave', 'drop'].forEach(name => {
    dropzone.addEventListener(name, (e) => {
      e.preventDefault();
      dropzone.classList.remove('drag-over');
    });
  });

  dropzone.addEventListener('drop', (e) => {
    if (e.dataTransfer && e.dataTransfer.files) {
      Array.from(e.dataTransfer.files).forEach(uploadFile);
    }
  });

  // Initial load
  loadStats();
  loadBuckets();
  setInterval(loadStats, 10000);
});
