import { useState, useEffect } from 'react';
import { Box, Typography, Card, CardContent, Chip, LinearProgress, Grid, Divider, Button, IconButton } from '@mui/material';
import { api } from '../services/api';
import FiberManualRecordIcon from '@mui/icons-material/FiberManualRecord';
import PhoneAndroidIcon from '@mui/icons-material/PhoneAndroid';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import { useNavigate } from 'react-router-dom';
import Pairing from './Pairing';

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
    const navigate = useNavigate();
    const [connectedClients, setConnectedClients] = useState<Client[]>([]);
    const [allClients, setAllClients] = useState<Client[]>([]);
    const [loading, setLoading] = useState(true);
    const [pairingOpen, setPairingOpen] = useState(false);

    useEffect(() => {
        const fetchClients = async () => {
            try {
                const response = await api.getClients();
                const clients = response.data.clients;

                const connected = clients.filter((c: Client) => c.isOnline);

                setConnectedClients(connected);
                setAllClients(clients);
            } catch (err) {
                console.error('Error fetching clients:', err);
            } finally {
                setLoading(false);
            }
        };

        fetchClients();
        // Poll for updates every 5 seconds
        const interval = setInterval(fetchClients, 5000);
        return () => clearInterval(interval);
    }, []);

    // ... (existing imports)

    // ... (inside ClientCard)
    const handleDelete = async (e: React.MouseEvent, id: number) => {
        e.stopPropagation();
        if (window.confirm('Are you sure you want to remove this device? This will delete all its metadata from the server.')) {
            try {
                await api.deleteClient(id);
                // Refresh list
                const newAll = allClients.filter(c => c.id !== id);
                setAllClients(newAll);
                setConnectedClients(newAll.filter(c => c.isOnline));
            } catch (err) {
                console.error('Failed to delete client:', err);
                alert('Failed to remove client');
            }
        }
    };

    const ClientCard = ({ client }: { client: Client }) => (
        <Card sx={{ mb: 2, cursor: 'pointer' }} onClick={() => navigate(`/clients/${client.id}`)}>
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

                    <Box display="flex" gap={1}>
                        <IconButton
                            size="small"
                            color="error"
                            onClick={(e) => handleDelete(e, client.id)}
                            title="Remove Device"
                        >
                            <DeleteIcon />
                        </IconButton>
                        <Chip label="Details" size="small" variant="outlined" onClick={() => navigate(`/clients/${client.id}`)} />
                    </Box>
                </Box>
            </CardContent>
        </Card>
    );

    return (
        <Box p={3}>
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={4}>
                <Typography variant="h4" fontWeight="bold">
                    Clients
                </Typography>
                <Button
                    variant="contained"
                    startIcon={<AddIcon />}
                    onClick={() => setPairingOpen(true)}
                >
                    Pair Device
                </Button>
            </Box>

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

            <Pairing open={pairingOpen} onClose={() => setPairingOpen(false)} />
        </Box>
    );
}
