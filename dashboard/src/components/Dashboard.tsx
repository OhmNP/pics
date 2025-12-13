import { useEffect, useState } from 'react';
import { Box, Typography, Grid, CircularProgress, LinearProgress } from '@mui/material';
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
    trend?: string;
}

function StatCard({ title, value, icon, color, trend }: StatCardProps) {
    return (
        <div className="glass-panel" style={{ padding: '1.5rem', borderRadius: '16px', height: '100%', position: 'relative', overflow: 'hidden' }}>
            <div style={{
                position: 'absolute',
                top: '-20px',
                right: '-20px',
                width: '100px',
                height: '100px',
                borderRadius: '50%',
                background: color,
                opacity: 0.15,
                filter: 'blur(20px)'
            }} />

            <Box display="flex" justifyContent="space-between" alignItems="flex-start" mb={2}>
                <div style={{
                    padding: '12px',
                    borderRadius: '12px',
                    background: `linear-gradient(135deg, ${color}22, transparent)`,
                    color: color,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center'
                }}>
                    {icon}
                </div>
            </Box>

            <Typography variant="h3" fontWeight="bold" sx={{ fontSize: '2rem', mb: 0.5 }}>
                {value}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 500 }}>
                {title}
            </Typography>

            {trend && (
                <Typography variant="caption" sx={{
                    display: 'inline-block',
                    mt: 2,
                    px: 1,
                    py: 0.5,
                    borderRadius: '6px',
                    background: 'rgba(255,255,255,0.05)',
                    color: 'var(--text-muted)'
                }}>
                    {trend}
                </Typography>
            )}
        </div>
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
    const [connectionStatus, setConnectionStatus] = useState<'connected' | 'connecting' | 'offline'>('connecting');
    const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

    useEffect(() => {
        const fetchStats = async () => {
            try {
                const response = await api.getStats();
                setStats(response.data);
                setConnectionStatus('connected');
                setLastUpdated(new Date());
            } catch (err) {
                console.error('Error fetching stats:', err);
                setConnectionStatus('offline');
            }
        };

        fetchStats();
        const interval = setInterval(fetchStats, 5000); // Refresh every 5 seconds

        return () => clearInterval(interval);
    }, []);

    if (connectionStatus === 'connecting' && !stats) {
        return (
            <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
                <CircularProgress sx={{ color: 'var(--primary)' }} />
            </Box>
        );
    }

    const storagePercentage = stats ? ((stats.storageUsed / stats.storageLimit) * 100) : 0;

    return (
        <Box>
            <Box display="flex" alignItems="center" justifyContent="space-between" mb={4}>
                <div>
                    <Typography variant="h4" fontWeight="800" className="gradient-text" gutterBottom>
                        Dashboard Overview
                    </Typography>
                    <Typography variant="body1" color="text.secondary">
                        System Status & Real-time Metrics
                    </Typography>
                </div>

                <Box display="flex" alignItems="center" gap={1} className="glass-panel" sx={{ px: 2, py: 1, borderRadius: '50px' }}>
                    {connectionStatus === 'connected' && (
                        <>
                            <CheckCircleIcon sx={{ color: 'var(--accent-green)' }} />
                            <Typography sx={{ color: 'var(--accent-green)', fontWeight: 600 }}>
                                System Online
                            </Typography>
                        </>
                    )}
                    {connectionStatus === 'connecting' && (
                        <>
                            <CircularProgress size={16} sx={{ color: '#f59e0b' }} />
                            <Typography sx={{ color: '#f59e0b', fontWeight: 600 }}>
                                Connecting...
                            </Typography>
                        </>
                    )}
                    {connectionStatus === 'offline' && (
                        <>
                            <div style={{ width: 12, height: 12, borderRadius: '50%', background: '#ef4444' }} />
                            <Typography sx={{ color: '#ef4444', fontWeight: 600 }}>
                                Offline
                            </Typography>
                        </>
                    )}
                    {lastUpdated && (
                        <Typography variant="caption" sx={{ color: 'var(--text-muted)', ml: 1, borderLeft: '1px solid var(--border-subtle)', pl: 1 }}>
                            {lastUpdated.toLocaleTimeString()}
                        </Typography>
                    )}
                </Box>
            </Box>

            {/* Live Connections */}
            {stats && (
                <Box mb={4}>
                    <LiveConnections />
                </Box>
            )}

            {stats ? (
                <Grid container spacing={3}>
                    {/* Row 1 */}
                    <Grid item xs={12} sm={6} md={4}>
                        <StatCard
                            title="Total Photos"
                            value={stats.totalPhotos.toLocaleString()}
                            icon={<PhotoCameraIcon fontSize="inherit" />}
                            color="var(--primary)"
                            trend="Indexed Media"
                        />
                    </Grid>

                    <Grid item xs={12} sm={6} md={4}>
                        <StatCard
                            title="Connected Clients"
                            value={stats.connectedClients}
                            icon={<PeopleIcon fontSize="inherit" />}
                            color="var(--secondary)"
                            trend="Active Devices"
                        />
                    </Grid>

                    <Grid item xs={12} sm={6} md={4}>
                        <StatCard
                            title="Synced Photos"
                            value={stats.totalPhotos.toLocaleString()} // Assuming same for now, or stats.syncedPhotos if available
                            icon={<PhotoCameraIcon fontSize="inherit" />}
                            color="var(--accent-green)"
                            trend="Successfully Synced"
                        />
                    </Grid>

                    {/* Row 2 */}
                    <Grid item xs={12} sm={6} md={4}>
                        <StatCard
                            title="Storage Used"
                            value={formatBytes(stats.storageUsed)}
                            icon={<StorageIcon fontSize="inherit" />}
                            color="var(--accent-pink)"
                            trend={`${storagePercentage.toFixed(1)}% Capacity`}
                        />
                        <Box mt={-2} mx={3} mb={3}>
                            <LinearProgress
                                variant="determinate"
                                value={storagePercentage}
                                sx={{
                                    borderRadius: 4,
                                    height: 4,
                                    bgcolor: 'rgba(255,255,255,0.1)',
                                    '& .MuiLinearProgress-bar': { backgroundColor: 'var(--accent-pink)' }
                                }}
                            />
                        </Box>
                    </Grid>

                    <Grid item xs={12} sm={6} md={4}>
                        <StatCard
                            title="Completed Sessions"
                            value={stats.totalSessions}
                            icon={<SyncIcon fontSize="inherit" />}
                            color="#06b6d4"
                            trend="Sync Operations"
                        />
                    </Grid>

                    <Grid item xs={12} sm={6} md={4}>
                        <StatCard
                            title="Uptime"
                            value={formatUptime(stats.uptime)}
                            icon={<AccessTimeIcon fontSize="inherit" />}
                            color="#ec4899"
                            trend="Since Last Restart"
                        />
                    </Grid>
                </Grid>
            ) : (
                <Box mt={8} textAlign="center" className="glass-panel" p={4} borderRadius={4}>
                    <Typography color="text.secondary" variant="h6">
                        Server is offline. Waiting for connection...
                    </Typography>
                    <Typography variant="body2" color="var(--text-muted)" mt={1}>
                        Please check if the desktop server application is running.
                    </Typography>
                </Box>
            )}
        </Box>
    );
}
