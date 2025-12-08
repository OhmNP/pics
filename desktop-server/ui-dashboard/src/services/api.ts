import axios from 'axios';

const API_BASE = 'http://localhost:50506/api';

// Add request interceptor to include auth token
axios.interceptors.request.use((config) => {
    const token = localStorage.getItem('sessionToken');
    if (token && config.url?.startsWith(API_BASE)) {
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
    getSessions: (page = 1, limit = 50) => axios.get(`${API_BASE}/sessions`, { params: { page, limit } }),
    getConfig: () => axios.get<ServerConfig>(`${API_BASE}/config`),
    updateConfig: (config: Partial<ServerConfig>) => axios.post(`${API_BASE}/config`, config),
    // Media grid
    getMedia: (offset = 0, limit = 50, clientId = 0, startDate = '', endDate = '', search = '') => axios.get(`${API_BASE}/media`, { params: { offset, limit, clientId, startDate, endDate, search } }),
};
