document.addEventListener('DOMContentLoaded', () => {
  let activeBucket = null;
  let activeBucketVersioning = 'OFF';
  let showVersions = false;
  let eventCount = 0;

  // Telemetry DOM
  const statRawBytes = document.getElementById('stat-raw-bytes');
  const statDiskBytes = document.getElementById('stat-disk-bytes');
  const statDedupRatio = document.getElementById('stat-dedup-ratio');
  const statSavedPercent = document.getElementById('stat-saved-percent');
  const statUniqueChunks = document.getElementById('stat-unique-chunks');

  // Buckets & Objects DOM
  const bucketListEl = document.getElementById('bucket-list');
  const currentBucketTitle = document.getElementById('current-bucket-title');
  const breadcrumbEl = document.getElementById('breadcrumb');
  const bucketActions = document.getElementById('bucket-actions');
  const bucketVersioningBadge = document.getElementById('bucket-versioning-badge');
  const btnToggleVersioning = document.getElementById('btn-toggle-versioning');
  const chkShowVersions = document.getElementById('chk-show-versions');
  const dropzone = document.getElementById('dropzone');
  const fileInput = document.getElementById('file-input');
  const browseLink = document.getElementById('browse-link');
  const uploadProgress = document.getElementById('upload-progress');
  const progressFill = document.getElementById('progress-fill');
  const progressText = document.getElementById('progress-text');
  const objectsTbody = document.getElementById('objects-tbody');

  // Header & Controls DOM
  const btnNewBucket = document.getElementById('btn-new-bucket');
  const btnDeleteBucket = document.getElementById('btn-delete-bucket');
  const btnGc = document.getElementById('btn-gc');
  const btnScrub = document.getElementById('btn-scrub');
  const sseStatus = document.getElementById('sse-status');
  const toast = document.getElementById('toast');

  // Activity Stream DOM
  const activityStream = document.getElementById('activity-stream');
  const activityCount = document.getElementById('activity-count');
  const btnClearActivity = document.getElementById('btn-clear-activity');

  // Preview Modal DOM
  const previewModal = document.getElementById('preview-modal');
  const previewTitle = document.getElementById('preview-title');
  const previewSubtitle = document.getElementById('preview-subtitle');
  const previewContent = document.getElementById('preview-content');
  const previewDownloadLink = document.getElementById('preview-download-link');
  const btnClosePreview = document.getElementById('btn-close-preview');
  const btnDismissPreview = document.getElementById('btn-dismiss-preview');

  // Presign Modal DOM
  const presignModal = document.getElementById('presign-modal');
  const presignKeyInput = document.getElementById('presign-key');
  const presignMethodSelect = document.getElementById('presign-method');
  const presignExpiresSelect = document.getElementById('presign-expires');
  const btnGeneratePresign = document.getElementById('btn-generate-presign');
  const presignResultContainer = document.getElementById('presign-result-container');
  const presignUrlOutput = document.getElementById('presign-url-output');
  const btnCopyPresign = document.getElementById('btn-copy-presign');
  const presignOpenLink = document.getElementById('presign-open-link');
  const btnClosePresign = document.getElementById('btn-close-presign');

  function formatBytes(bytes) {
    if (!bytes || bytes === 0) return '0 B';
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

  function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
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
            <span>${escapeHtml(b.name)}</span>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9 18 15 12 9 6"></polyline>
            </svg>
          `;
          li.addEventListener('click', () => selectBucket(b.name, b.versioningStatus));
          bucketListEl.appendChild(li);
        });

        if (activeBucket) {
          const current = buckets.find(b => b.name === activeBucket);
          if (current) {
            updateVersioningBadge(current.versioningStatus);
          } else {
            selectBucket(null);
          }
        }
      }
    } catch (e) {
      bucketListEl.innerHTML = '<li class="empty-item">Error loading buckets.</li>';
    }
  }

  function updateVersioningBadge(status) {
    activeBucketVersioning = status || 'OFF';
    bucketVersioningBadge.style.display = 'inline-block';
    bucketVersioningBadge.className = 'versioning-badge';

    if (activeBucketVersioning === 'ENABLED') {
      bucketVersioningBadge.classList.add('badge-on');
      bucketVersioningBadge.textContent = 'Versioning: ON';
      btnToggleVersioning.textContent = 'Suspend Versioning';
    } else if (activeBucketVersioning === 'SUSPENDED') {
      bucketVersioningBadge.classList.add('badge-suspended');
      bucketVersioningBadge.textContent = 'Versioning: SUSPENDED';
      btnToggleVersioning.textContent = 'Resume Versioning';
    } else {
      bucketVersioningBadge.classList.add('badge-off');
      bucketVersioningBadge.textContent = 'Versioning: OFF';
      btnToggleVersioning.textContent = 'Enable Versioning';
    }
  }

  async function selectBucket(name, versioningStatus) {
    activeBucket = name;
    document.querySelectorAll('.bucket-item').forEach(el => {
      el.classList.toggle('active', el.querySelector('span')?.textContent === name);
    });

    if (!name) {
      currentBucketTitle.textContent = 'Select a bucket';
      breadcrumbEl.textContent = '/';
      bucketActions.style.display = 'none';
      bucketVersioningBadge.style.display = 'none';
      dropzone.style.display = 'none';
      objectsTbody.innerHTML = '<tr><td colspan="7" class="empty-state">Select a bucket from the sidebar to browse objects.</td></tr>';
      return;
    }

    currentBucketTitle.textContent = name;
    breadcrumbEl.textContent = 's3://' + name + '/';
    bucketActions.style.display = 'flex';
    dropzone.style.display = 'block';

    if (versioningStatus) {
      updateVersioningBadge(versioningStatus);
    }

    await loadObjects(name);
  }

  async function loadObjects(bucketName) {
    objectsTbody.innerHTML = '<tr><td colspan="7" class="empty-state">Loading objects...</td></tr>';
    const endpoint = showVersions
      ? `/api/admin/buckets/${encodeURIComponent(bucketName)}/versions`
      : `/api/admin/buckets/${encodeURIComponent(bucketName)}/objects`;

    try {
      const res = await fetch(endpoint);
      if (res.ok) {
        const objects = await res.json();
        if (objects.length === 0) {
          objectsTbody.innerHTML = '<tr><td colspan="7" class="empty-state">This bucket is empty. Drag and drop files above to upload.</td></tr>';
          return;
        }

        objectsTbody.innerHTML = '';
        objects.forEach(obj => {
          const tr = document.createElement('tr');
          const lastMod = new Date(obj.createdAt).toLocaleString();
          const cleanEtag = (obj.etag || '').replace(/"/g, '');

          let versionCell = '<span class="badge-version">latest</span>';
          if (obj.deleteMarker) {
            versionCell = '<span class="badge-version badge-delete-marker">Delete Marker</span>';
          } else if (obj.versionId && obj.versionId !== 'null') {
            const shortVer = obj.versionId.substring(0, 8);
            versionCell = `<span class="badge-version" title="${escapeHtml(obj.versionId)}">${shortVer}</span>`;
          }

          const downloadUrl = `/${encodeURIComponent(bucketName)}/${encodeURIComponent(obj.key)}` +
            (obj.versionId && obj.versionId !== 'null' ? `?versionId=${encodeURIComponent(obj.versionId)}` : '');

          tr.innerHTML = `
            <td>
              <a href="#" class="obj-link" data-key="${escapeHtml(obj.key)}" data-ver="${escapeHtml(obj.versionId || '')}" data-type="${escapeHtml(obj.contentType || '')}" data-size="${obj.size}">
                <strong>${escapeHtml(obj.key)}</strong>
              </a>
            </td>
            <td>${versionCell}</td>
            <td>${escapeHtml(obj.contentType || 'application/octet-stream')}</td>
            <td>${obj.deleteMarker ? '-' : formatBytes(obj.size)}</td>
            <td><code>${cleanEtag ? cleanEtag.substring(0, 12) + '...' : '-'}</code></td>
            <td>${lastMod}</td>
            <td style="text-align: right; white-space: nowrap;">
              ${!obj.deleteMarker ? `<a href="#" class="action-link action-preview" data-key="${escapeHtml(obj.key)}" data-ver="${escapeHtml(obj.versionId || '')}" data-type="${escapeHtml(obj.contentType || '')}" data-size="${obj.size}">Preview</a>` : ''}
              ${!obj.deleteMarker ? `<a href="#" class="action-link action-presign" data-key="${escapeHtml(obj.key)}">Presign</a>` : ''}
              ${!obj.deleteMarker ? `<a href="${downloadUrl}" target="_blank" class="action-link">Download</a>` : ''}
              <a href="#" class="action-link action-delete" data-key="${escapeHtml(obj.key)}" data-ver="${escapeHtml(obj.versionId || '')}">Delete</a>
            </td>
          `;

          // Bind preview
          tr.querySelectorAll('.action-preview, .obj-link').forEach(link => {
            link.addEventListener('click', (e) => {
              e.preventDefault();
              if (!obj.deleteMarker) {
                openPreview(bucketName, obj.key, obj.versionId, obj.contentType, obj.size);
              }
            });
          });

          // Bind presign
          const presignBtn = tr.querySelector('.action-presign');
          if (presignBtn) {
            presignBtn.addEventListener('click', (e) => {
              e.preventDefault();
              openPresign(bucketName, obj.key);
            });
          }

          // Bind delete
          tr.querySelector('.action-delete').addEventListener('click', (e) => {
            e.preventDefault();
            deleteObject(bucketName, obj.key, obj.versionId);
          });

          objectsTbody.appendChild(tr);
        });
      }
    } catch (e) {
      objectsTbody.innerHTML = '<tr><td colspan="7" class="empty-state">Error loading objects.</td></tr>';
    }
  }

  // Versioning Toggle
  btnToggleVersioning.addEventListener('click', async () => {
    if (!activeBucket) return;
    const nextStatus = (activeBucketVersioning === 'OFF' || activeBucketVersioning === 'SUSPENDED') ? 'ENABLED' : 'SUSPENDED';
    try {
      const res = await fetch(`/api/admin/buckets/${encodeURIComponent(activeBucket)}/versioning?status=${nextStatus}`, { method: 'PUT' });
      if (res.ok) {
        updateVersioningBadge(nextStatus);
        showToast(`Bucket versioning set to ${nextStatus}`);
        loadObjects(activeBucket);
      } else {
        showToast('Failed to update bucket versioning');
      }
    } catch (e) {
      showToast('Error setting versioning');
    }
  });

  chkShowVersions.addEventListener('change', () => {
    showVersions = chkShowVersions.checked;
    if (activeBucket) {
      loadObjects(activeBucket);
    }
  });

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
        selectBucket(name.trim(), 'OFF');
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

  // Delete object / version
  async function deleteObject(bucket, key, versionId) {
    const targetDesc = versionId && versionId !== 'null' ? `version ${versionId} of '${key}'` : `'${key}'`;
    if (!confirm(`Delete ${targetDesc}?`)) return;

    let url = `/api/admin/buckets/${encodeURIComponent(bucket)}/objects?key=${encodeURIComponent(key)}`;
    if (versionId && versionId !== 'null') {
      url += `&versionId=${encodeURIComponent(versionId)}`;
    }

    try {
      const res = await fetch(url, { method: 'DELETE' });
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

  // Integrity Scrub
  btnScrub.addEventListener('click', async () => {
    btnScrub.disabled = true;
    btnScrub.textContent = 'Auditing...';
    try {
      const res = await fetch('/api/admin/scrub', { method: 'POST' });
      if (res.ok) {
        const report = await res.json();
        if (report.corruptedChunks === 0) {
          showToast(`Scrub Complete: All ${report.totalChunks} CAS chunks verified healthy.`);
        } else {
          showToast(`Bitrot Detected: ${report.corruptedChunks} corrupted chunks out of ${report.totalChunks}.`);
        }
        loadStats();
      }
    } catch (e) {
      showToast('Storage scrub failed');
    } finally {
      btnScrub.disabled = false;
      btnScrub.innerHTML = `
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
        </svg>
        Scrub Storage
      `;
    }
  });

  // Media & Document Preview Modal
  async function openPreview(bucket, key, versionId, contentType, size) {
    previewTitle.textContent = key;
    previewSubtitle.textContent = `${contentType || 'application/octet-stream'} • ${formatBytes(size)}`;
    const downloadUrl = `/${encodeURIComponent(bucket)}/${encodeURIComponent(key)}` +
      (versionId && versionId !== 'null' ? `?versionId=${encodeURIComponent(versionId)}` : '');
    previewDownloadLink.href = downloadUrl;

    previewContent.innerHTML = '<div class="preview-loading">Loading content...</div>';
    previewModal.style.display = 'flex';

    const ct = (contentType || '').toLowerCase();
    const isImage = ct.startsWith('image/') || /\.(png|jpg|jpeg|gif|webp|svg)$/i.test(key);
    const isAudio = ct.startsWith('audio/') || /\.(mp3|wav|ogg)$/i.test(key);
    const isVideo = ct.startsWith('video/') || /\.(mp4|webm)$/i.test(key);
    const isText = ct.startsWith('text/') || ct.includes('json') || ct.includes('xml') || /\.(txt|json|xml|md|java|py|js|html|css|yaml|yml|sql)$/i.test(key);

    if (isImage) {
      previewContent.innerHTML = `<img src="${downloadUrl}" alt="${escapeHtml(key)}" class="preview-img">`;
    } else if (isAudio) {
      previewContent.innerHTML = `<audio controls src="${downloadUrl}" class="preview-audio"></audio>`;
    } else if (isVideo) {
      previewContent.innerHTML = `<video controls src="${downloadUrl}" class="preview-video"></video>`;
    } else if (isText && size < 2 * 1024 * 1024) {
      try {
        const res = await fetch(downloadUrl);
        const text = await res.text();
        previewContent.innerHTML = `<pre class="code-view"><code>${escapeHtml(text)}</code></pre>`;
      } catch (e) {
        previewContent.innerHTML = '<div class="preview-fallback">Failed to fetch text content.</div>';
      }
    } else {
      previewContent.innerHTML = `
        <div class="preview-fallback">
          <p>Binary or Large File (${formatBytes(size)})</p>
          <p class="text-muted">Use the download button below to inspect this object locally.</p>
        </div>
      `;
    }
  }

  function closePreview() {
    previewModal.style.display = 'none';
    previewContent.innerHTML = '';
  }

  btnClosePreview.addEventListener('click', closePreview);
  btnDismissPreview.addEventListener('click', closePreview);
  previewModal.addEventListener('click', (e) => {
    if (e.target === previewModal) closePreview();
  });

  // Presign Modal
  let presignTargetKey = null;
  function openPresign(bucket, key) {
    presignTargetKey = key;
    presignKeyInput.value = `s3://${bucket}/${key}`;
    presignResultContainer.style.display = 'none';
    presignModal.style.display = 'flex';
  }

  function closePresign() {
    presignModal.style.display = 'none';
    presignResultContainer.style.display = 'none';
  }

  btnClosePresign.addEventListener('click', closePresign);
  presignModal.addEventListener('click', (e) => {
    if (e.target === presignModal) closePresign();
  });

  btnGeneratePresign.addEventListener('click', async () => {
    if (!activeBucket || !presignTargetKey) return;
    const method = presignMethodSelect.value;
    const expiresIn = presignExpiresSelect.value;

    try {
      btnGeneratePresign.disabled = true;
      const res = await fetch(`/api/admin/presign?bucket=${encodeURIComponent(activeBucket)}&key=${encodeURIComponent(presignTargetKey)}&method=${method}&expiresIn=${expiresIn}`, { method: 'POST' });
      if (res.ok) {
        const data = await res.json();
        presignUrlOutput.value = data.url;
        presignOpenLink.href = data.url;
        presignResultContainer.style.display = 'block';
      } else {
        showToast('Failed to generate presigned URL');
      }
    } catch (e) {
      showToast('Error connecting to presign API');
    } finally {
      btnGeneratePresign.disabled = false;
    }
  });

  btnCopyPresign.addEventListener('click', () => {
    presignUrlOutput.select();
    navigator.clipboard.writeText(presignUrlOutput.value);
    btnCopyPresign.textContent = 'Copied!';
    setTimeout(() => { btnCopyPresign.textContent = 'Copy'; }, 2000);
  });

  // Live SSE Activity Stream
  function initSSE() {
    const eventSource = new EventSource('/api/admin/events');

    eventSource.onopen = () => {
      sseStatus.querySelector('.status-dot').className = 'status-dot green';
      document.getElementById('sse-text').textContent = 'Live Telemetry';
    };

    eventSource.onerror = () => {
      sseStatus.querySelector('.status-dot').className = 'status-dot';
      document.getElementById('sse-text').textContent = 'Telemetry Reconnecting...';
    };

    const handleEvent = (event) => {
      try {
        const data = JSON.parse(event.data);
        appendActivityItem(data);

        // Auto-refresh objects or stats if relevant
        if (data.type === 'OBJECT_CREATED' || data.type === 'OBJECT_DELETED' || data.type === 'BATCH_DELETE') {
          if (activeBucket && activeBucket === data.bucket) {
            loadObjects(activeBucket);
          }
          loadStats();
        } else if (data.type === 'BUCKET_CREATED' || data.type === 'BUCKET_DELETED') {
          loadBuckets();
          loadStats();
        } else if (data.type === 'VERSIONING_CHANGED') {
          loadBuckets();
        }
      } catch (e) {
        console.error('Error handling SSE event', e);
      }
    };

    eventSource.addEventListener('CONNECTED', handleEvent);
    eventSource.addEventListener('OBJECT_CREATED', handleEvent);
    eventSource.addEventListener('OBJECT_DELETED', handleEvent);
    eventSource.addEventListener('BATCH_DELETE', handleEvent);
    eventSource.addEventListener('BUCKET_CREATED', handleEvent);
    eventSource.addEventListener('BUCKET_DELETED', handleEvent);
    eventSource.addEventListener('VERSIONING_CHANGED', handleEvent);
    eventSource.addEventListener('SCRUB_REPORT', handleEvent);
    eventSource.addEventListener('GC_RUN', handleEvent);
    eventSource.addEventListener('CREDENTIAL_CREATED', handleEvent);
    eventSource.addEventListener('CREDENTIAL_DELETED', handleEvent);
  }

  function appendActivityItem(evt) {
    if (activityStream.querySelector('.activity-empty')) {
      activityStream.innerHTML = '';
    }

    eventCount++;
    activityCount.textContent = `${eventCount} events`;

    const item = document.createElement('div');
    item.className = 'activity-item';

    let badgeClass = 'badge-conn';
    if (evt.type.includes('CREATED') || evt.type === 'OBJECT_CREATED') badgeClass = 'badge-put';
    else if (evt.type.includes('DELETE')) badgeClass = 'badge-del';
    else if (evt.type.includes('SCRUB')) badgeClass = 'badge-scrub';
    else if (evt.type.includes('GC')) badgeClass = 'badge-gc';

    const timeStr = new Date(evt.timestamp).toLocaleTimeString();
    const pathStr = evt.bucket ? (evt.key ? `${evt.bucket}/${evt.key}` : evt.bucket) : '';

    item.innerHTML = `
      <span class="activity-time">${timeStr}</span>
      <span class="activity-badge ${badgeClass}">${evt.type}</span>
      <span class="activity-meta">${pathStr ? `<strong>${escapeHtml(pathStr)}</strong> &bull; ` : ''}${escapeHtml(evt.details || '')}</span>
    `;

    activityStream.prepend(item);
    // Keep max 50 items
    while (activityStream.children.length > 50) {
      activityStream.removeChild(activityStream.lastChild);
    }
  }

  btnClearActivity.addEventListener('click', () => {
    activityStream.innerHTML = '<div class="activity-empty">Awaiting S3 events...</div>';
    eventCount = 0;
    activityCount.textContent = '0 events';
  });

  // Dropzone drag-and-drop
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
  initSSE();
  setInterval(loadStats, 10000);
});
