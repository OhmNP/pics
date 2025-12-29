import axios from 'axios';

const API_BASE = 'https://localhost:50506/api';

// Add request interceptor to include auth token
axios.interceptors.request.use((config) => {
    const token = localStorage.getItem('sessionToken');
    if (token && (config.url?.startsWith(API_BASE) || config.url?.startsWith('/api'))) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

// Add response interceptor to handle 401 errors
axios.interceptors.response.use(
    (response) => response,
    (error) => {
        if (error.response?.status === 401) {
            // Session expired or invalid
            localStorage.removeItem('sessionToken');
            localStorage.removeItem('user');
            window.location.href = '/login';
        }
        return Promise.reject(error);
    }
);

export interface ServerStats {
    totalPhotos: number;
    connectedClients: number;
    totalClients: number;
    totalSessions: number;
    storageUsed: number;
    storageLimit: number;
    uptime: number;
    serverStatus: string;
}

export interface ServerConfig {
    network: {
        port: number;
        maxConnections: number;
        timeout: number;
    };
    storage: {
        photosDir: string;
        dbPath: string;
        maxStorageGB: number;
    };
    logging: {
        level: string;
        file: string;
        consoleOutput: boolean;
    };
}

export interface LoginResponse {
    sessionToken: string;
    expiresAt: string;
    user: {
        id: number;
        username: string;
    };
}

export interface ValidateResponse {
    valid: boolean;
    expiresAt?: string;
}

export interface MediaItem {
    id: number;
    filename: string;
    thumbnailUrl: string;
    fullUrl: string;
    mimeType: string;
    size: number;
    uploadedAt: string;
    clientId: number;
}

export interface MediaResponse {
    items: MediaItem[];
    pagination: {
        offset: number;
        limit: number;
        total: number;
        hasMore: boolean;
    };
}

export interface Client {
    id: number;
    deviceId: string;
    name: string;
    lastSeen: string;
    photoCount: number;
    storageUsed: number;
    isOnline: boolean;
    currentSession?: {
        progress: number;
        total: number;
    };
}

export interface Session {
    id: number;
    clientId: number;
    deviceId: string;
    clientName: string;
    startedAt: string;
    endedAt: string;
    photosReceived: number;
    status: string;
}

export interface SessionsResponse {
    sessions: Session[];
    pagination: {
        page: number;
        limit: number;
        total: number;
        pages: number;
    };
}

export const api = {
    // Authentication
    login: (username: string, password: string) =>
        axios.post<LoginResponse>(`${API_BASE}/auth/login`, { username, password }),
    logout: (token: string) =>
        axios.post(`${API_BASE}/auth/logout`, {}, { headers: { Authorization: `Bearer ${token}` } }),
    validateSession: (token: string) =>
        axios.get<ValidateResponse>(`${API_BASE}/auth/validate`, { headers: { Authorization: `Bearer ${token}` } }),

    // Server data
    getStats: () => axios.get<ServerStats>(`${API_BASE}/stats`),
    getPhotos: (page = 1, limit = 50) => axios.get(`${API_BASE}/photos`, { params: { page, limit } }),
    getClients: () => axios.get(`${API_BASE}/clients`),
    getClientDetails: (id: number) => axios.get(`${API_BASE}/clients/${id}`).then((res) => res.data),
    deleteClient: (id: number) => axios.delete(`${API_BASE}/clients/${id}`).then((res) => res.data),
    revokeClient: (id: number) => axios.post(`${API_BASE}/devices/${id}/revoke`).then((res) => res.data),
    getSessions: (page = 1, limit = 10, clientId = "", status = "") => axios.get(`${API_BASE}/sessions`, { params: { page, limit } }),
    getConfig: () => axios.get<ServerConfig>(`${API_BASE}/config`),
    updateConfig: (config: Partial<ServerConfig>) => axios.post(`${API_BASE}/config`, config),
    // Media grid
    getMedia: (offset = 0, limit = 50, clientId = 0, startDate = '', endDate = '', search = '') => axios.get(`${API_BASE}/media`, { params: { offset, limit, clientId, startDate, endDate, search } }),

    // System
    getNetworkInfo: () => axios.get<{ ips: string[], port: number }>(`${API_BASE}/network`),

    // Phase 6: Operations
    getErrors: (limit = 50, offset = 0, level = "", deviceId = "", since = "") =>
        axios.get<ErrorsResponse>(`${API_BASE}/errors`, { params: { limit, offset, level, deviceId, since } }),
    getIntegrityStatus: () => axios.get<IntegrityReport>(`${API_BASE}/integrity`),
    getIntegrityDetails: (type: string, limit = 50) => axios.get<IntegrityDetailsResponse>(`${API_BASE}/integrity/details`, { params: { type, limit } }),
    getTopFiles: () => axios.get<TopFilesResponse>(`${API_BASE}/top-files`),
    getHealth: () => axios.get<HealthStats>(`${API_BASE}/health`),
    getChanges: (params: { cursor?: string, limit?: number } = {}) => axios.get<ChangesResponse>(`${API_BASE}/changes`, { params }),
};

export interface ChangeRecord {
    change_id: number;
    op: string;
    media_id: number;
    blob_hash: string;
    changed_at: string;
    filename: string;
    device_id: string;
}

export interface ChangesResponse {
    changes: ChangeRecord[];
    next_cursor: string;
    has_more: boolean;
}

export interface FileAnalysis {
    id: number;
    filename: string;
    mimeType: string;
    size: number;
    originalPath: string;
}

export interface TopFilesResponse {
    topFiles: FileAnalysis[];
}

export interface ErrorLog {
    id: number;
    code: number;
    message: string;
    traceId: string;
    timestamp: string;
    severity?: string;
    deviceId?: string;
    context?: string;
}

export interface ErrorsResponse {
    errors: ErrorLog[];
}

export interface IntegrityReport {
    status: string;
    lastScan: string;
    totalPhotos: number;
    missingBlobs: number;
    corruptBlobs: number;
    orphanBlobs: number;
    tombstones: number;
    message?: string;
}

export interface HealthStats {
    uptime: number;
    version: string;
    diskFree: number;
    diskTotal: number;
    dbSize: number;
    pendingUploads: number;
    failedUploads: number;
    activeSessions: number;
    lastIntegrityScan: string;
    integrityIssues: number;
}

export interface IntegrityDetailsResponse {
    type: string;
    items: string[];
}

