import { useEffect, useState } from 'react';
import { Box, Card, CardContent, Typography, Grid, CircularProgress } from '@mui/material';
import { api, ServerStats } from '../services/api';
import LiveConnections from './LiveConnections';
import PhotoCameraIcon from '@mui/icons-material/PhotoCamera';
import PeopleIcon from '@mui/icons-material/People';
import SyncIcon from '@mui/icons-material/Sync';
import StorageIcon from '@mui/icons-material/Storage';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';

interface StatCardProps {
    title: string;
    value: string | number;
    icon: React.ReactNode;
    color: string;
}

function StatCard({ title, value, icon, color }: StatCardProps) {
    return (
        <Card sx={{ height: '100%' }}>
            <CardContent>
                <Box display="flex" alignItems="center" justifyContent="space-between">
                    <Box>
                        <Typography variant="body2" color="text.secondary" gutterBottom>
                            {title}
                        </Typography>
                        <Typography variant="h4" fontWeight="bold">
                            {value}
                        </Typography>
                    </Box>
                    <Box sx={{ color, fontSize: 48, opacity: 0.8 }}>
                        {icon}
                    </Box>
                </Box>
            </CardContent>
        </Card>
    );
}

function formatBytes(bytes: number): string {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`;
}

function formatUptime(seconds: number): string {
    const days = Math.floor(seconds / 86400);
    const hours = Math.floor((seconds % 86400) / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);

    if (days > 0) return `${days}d ${hours}h`;
    if (hours > 0) return `${hours}h ${minutes}m`;
    return `${minutes}m`;
}

export default function Dashboard() {
    const [stats, setStats] = useState<ServerStats | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        const fetchStats = async () => {
            try {
                const response = await api.getStats();
                setStats(response.data);
                setError(null);
            } catch (err) {
                setError('Failed to fetch server statistics');
                console.error('Error fetching stats:', err);
            } finally {
                setLoading(false);
            }
        };

        fetchStats();
        const interval = setInterval(fetchStats, 5000); // Refresh every 5 seconds

        return () => clearInterval(interval);
    }, []);

    if (loading) {
        return (
            <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
                <CircularProgress />
            </Box>
        );
    }

    if (error || !stats) {
        return (
            <Box p={3}>
                <Typography color="error">{error || 'Failed to load data'}</Typography>
            </Box>
        );
    }

    const storagePercentage = ((stats.storageUsed / stats.storageLimit) * 100).toFixed(1);

    return (
        <Box p={3}>
            <Box display="flex" alignItems="center" justifyContent="space-between" mb={3}>
                <Typography variant="h4" fontWeight="bold">
                    PhotoSync Server Dashboard
                </Typography>
                <Box display="flex" alignItems="center" gap={1}>
                    {stats.serverStatus === 'running' && (
                        <>
                            <CheckCircleIcon sx={{ color: '#10b981' }} />
                            <Typography color="#10b981" fontWeight={500}>
                                Running
                            </Typography>
                        </>
                    )}
                </Box>
            </Box>

            {/* Live Connections */}
            <LiveConnections />

            <Grid container spacing={3}>
                {/* Row 1 */}
                <Grid item xs={12} sm={6} md={4}>
                    <StatCard
                        title="Total Photos"
                        value={stats.totalPhotos.toLocaleString()}
                        icon={<PhotoCameraIcon fontSize="inherit" />}
                        color="#00d9ff"
                    />
                </Grid>

                <Grid item xs={12} sm={6} md={4}>
                    <StatCard
                        title="Connected Clients"
                        value={stats.connectedClients}
                        icon={<PeopleIcon fontSize="inherit" />}
                        color="#8b5cf6"
                    />
                </Grid>

                <Grid item xs={12} sm={6} md={4}>
                    <StatCard
                        title="Synced Photos"
                        value={stats.totalPhotos.toLocaleString()}
                        icon={<PhotoCameraIcon fontSize="inherit" />}
                        color="#10b981"
                    />
                </Grid>

                {/* Row 2 */}
                <Grid item xs={12} sm={6} md={4}>
                    <StatCard
                        title="Storage Used"
                        value={`${formatBytes(stats.storageUsed)} (${storagePercentage}%)`}
                        icon={<StorageIcon fontSize="inherit" />}
                        color="#f59e0b"
                    />
                </Grid>

                <Grid item xs={12} sm={6} md={4}>
                    <StatCard
                        title="Completed Sessions"
                        value={stats.totalSessions}
                        icon={<SyncIcon fontSize="inherit" />}
                        color="#06b6d4"
                    />
                </Grid>

                <Grid item xs={12} sm={6} md={4}>
                    <StatCard
                        title="Uptime"
                        value={formatUptime(stats.uptime)}
                        icon={<AccessTimeIcon fontSize="inherit" />}
                        color="#ec4899"
                    />
                </Grid>
            </Grid>

            {/* Recent Activity Section (Mock data for now) */}
            <Box mt={4}>
                <Typography variant="h6" gutterBottom>
                    Recent Activity
                </Typography>
                <Card>
                    <CardContent>
                        <Typography color="text.secondary">
                            Real-time activity feed will be available with WebSocket implementation
                        </Typography>
                    </CardContent>
                </Card>
            </Box>
        </Box>
    );
}
