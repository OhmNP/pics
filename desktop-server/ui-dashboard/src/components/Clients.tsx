import { useState, useEffect } from 'react';
import { Box, Typography, Card, CardContent, Chip, LinearProgress, Grid, Divider } from '@mui/material';
import { api } from '../services/api';
import FiberManualRecordIcon from '@mui/icons-material/FiberManualRecord';
import PhoneAndroidIcon from '@mui/icons-material/PhoneAndroid';

interface Client {
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

function formatBytes(bytes: number): string {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${(bytes / Math.pow(k, i)).toFixed(1)} ${sizes[i]}`;
}

function formatTimeAgo(dateString: string): string {
    const date = new Date(dateString);
    const now = new Date();
    const seconds = Math.floor((now.getTime() - date.getTime()) / 1000);

    if (seconds < 60) return 'Just now';
    if (seconds < 3600) return `${Math.floor(seconds / 60)} minutes ago`;
    if (seconds < 86400) return `${Math.floor(seconds / 3600)} hours ago`;
    return `${Math.floor(seconds / 86400)} days ago`;
}

export default function Clients() {
    const [connectedClients, setConnectedClients] = useState<Client[]>([]);
    const [allClients, setAllClients] = useState<Client[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const fetchClients = async () => {
            try {
                await api.getClients();
                // Using mock data for UI demo
                const mockConnected: Client[] = [
                    {
                        id: 1,
                        deviceId: 'pixel-7-abc123',
                        name: 'Pixel 7',
                        lastSeen: new Date().toISOString(),
                        photoCount: 8432,
                        storageUsed: 2100000000,
                        isOnline: true,
                        currentSession: { progress: 12, total: 45 },
                    },
                    {
                        id: 2,
                        deviceId: 'galaxy-s21-def456',
                        name: 'Galaxy S21',
                        lastSeen: new Date(Date.now() - 120000).toISOString(),
                        photoCount: 5201,
                        storageUsed: 1500000000,
                        isOnline: true,
                    },
                ];

                const mockOffline: Client[] = [
                    {
                        id: 3,
                        deviceId: 'iphone-13-ghi789',
                        name: 'iPhone 13',
                        lastSeen: new Date(Date.now() - 10800000).toISOString(),
                        photoCount: 2214,
                        storageUsed: 546000000,
                        isOnline: false,
                    },
                    {
                        id: 4,
                        deviceId: 'oneplus-9-jkl012',
                        name: 'OnePlus 9',
                        lastSeen: new Date(Date.now() - 86400000).toISOString(),
                        photoCount: 0,
                        storageUsed: 0,
                        isOnline: false,
                    },
                ];

                setConnectedClients(mockConnected);
                setAllClients([...mockConnected, ...mockOffline]);
            } catch (err) {
                console.error('Error fetching clients:', err);
            } finally {
                setLoading(false);
            }
        };

        fetchClients();
    }, []);

    const ClientCard = ({ client }: { client: Client }) => (
        <Card sx={{ mb: 2 }}>
            <CardContent>
                <Box display="flex" alignItems="flex-start" justifyContent="space-between">
                    <Box display="flex" gap={2} flex={1}>
                        <Box
                            sx={{
                                width: 48,
                                height: 48,
                                borderRadius: 2,
                                backgroundColor: 'rgba(0, 217, 255, 0.1)',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                            }}
                        >
                            <PhoneAndroidIcon sx={{ color: '#00d9ff' }} />
                        </Box>

                        <Box flex={1}>
                            <Box display="flex" alignItems="center" gap={1} mb={0.5}>
                                <FiberManualRecordIcon
                                    sx={{
                                        fontSize: 12,
                                        color: client.isOnline ? '#10b981' : '#6b7280',
                                    }}
                                />
                                <Typography variant="h6" fontWeight={600}>
                                    {client.name}
                                </Typography>
                            </Box>

                            <Typography variant="caption" color="text.secondary" display="block" mb={1}>
                                Last seen: {formatTimeAgo(client.lastSeen)}
                            </Typography>

                            <Box display="flex" gap={2} flexWrap="wrap">
                                <Typography variant="body2" color="text.secondary">
                                    Photos: <strong>{client.photoCount.toLocaleString()}</strong>
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                    Storage: <strong>{formatBytes(client.storageUsed)}</strong>
                                </Typography>
                            </Box>

                            {client.currentSession && (
                                <Box mt={2}>
                                    <Box display="flex" justifyContent="space-between" mb={0.5}>
                                        <Typography variant="caption" color="text.secondary">
                                            Syncing...
                                        </Typography>
                                        <Typography variant="caption" color="text.secondary">
                                            {client.currentSession.progress} of {client.currentSession.total} photos
                                        </Typography>
                                    </Box>
                                    <LinearProgress
                                        variant="determinate"
                                        value={(client.currentSession.progress / client.currentSession.total) * 100}
                                        sx={{ height: 6, borderRadius: 3 }}
                                    />
                                </Box>
                            )}

                            {!client.currentSession && client.isOnline && (
                                <Chip label="Idle" size="small" color="success" variant="outlined" sx={{ mt: 1 }} />
                            )}
                        </Box>
                    </Box>

                    <Chip label="Details" size="small" variant="outlined" />
                </Box>
            </CardContent>
        </Card>
    );

    return (
        <Box p={3}>
            <Typography variant="h4" fontWeight="bold" gutterBottom>
                Clients
            </Typography>

            {/* Connected Clients */}
            <Box mb={4}>
                <Typography variant="h6" gutterBottom>
                    Connected Now ({connectedClients.length})
                </Typography>
                {connectedClients.map((client) => (
                    <ClientCard key={client.id} client={client} />
                ))}
            </Box>

            <Divider sx={{ my: 3 }} />

            {/* All Clients */}
            <Box>
                <Typography variant="h6" gutterBottom>
                    All Clients ({allClients.length})
                </Typography>
                <Grid container spacing={2}>
                    {allClients.map((client) => (
                        <Grid item xs={12} key={client.id}>
                            <ClientCard client={client} />
                        </Grid>
                    ))}
                </Grid>
            </Box>
        </Box>
    );
}
