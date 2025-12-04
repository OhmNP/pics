import axios from 'axios';

const API_BASE = 'http://localhost:50506/api';

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

export const api = {
    getStats: () => axios.get<ServerStats>(`${API_BASE}/stats`),
    getPhotos: (page = 1, limit = 50) => axios.get(`${API_BASE}/photos`, { params: { page, limit } }),
    getClients: () => axios.get(`${API_BASE}/clients`),
    getSessions: (page = 1, limit = 50) => axios.get(`${API_BASE}/sessions`, { params: { page, limit } }),
    getConfig: () => axios.get<ServerConfig>(`${API_BASE}/config`),
    updateConfig: (config: Partial<ServerConfig>) => axios.post(`${API_BASE}/config`, config),
};
