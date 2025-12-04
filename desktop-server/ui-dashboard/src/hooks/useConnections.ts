import { useState, useEffect } from 'react';

export interface Connection {
    session_id: number;
    device_id: string;
    ip_address: string;
    connected_at: string;
    status: string;
    photos_uploaded: number;
    bytes_transferred: number;
    last_activity: string;
    duration_seconds: number;
}

export interface ConnectionsResponse {
    active_connections: Connection[];
    total_active: number;
}

export function useConnections(refreshInterval = 3000) {
    const [connections, setConnections] = useState<Connection[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        const fetchConnections = async () => {
            try {
                const response = await fetch('http://localhost:50506/api/connections');
                if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                }
                const data: ConnectionsResponse = await response.json();
                setConnections(data.active_connections);
                setError(null);
            } catch (err) {
                setError(err instanceof Error ? err.message : 'Failed to fetch connections');
                console.error('Error fetching connections:', err);
            } finally {
                setLoading(false);
            }
        };

        // Initial fetch
        fetchConnections();

        // Set up polling
        const interval = setInterval(fetchConnections, refreshInterval);

        return () => clearInterval(interval);
    }, [refreshInterval]);

    return { connections, loading, error };
}
