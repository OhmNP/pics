import { useConnections, Connection } from '../hooks/useConnections';
import { Box, Typography, Grid, LinearProgress, Chip } from '@mui/material';
import SmartphoneIcon from '@mui/icons-material/Smartphone';
import WifiIcon from '@mui/icons-material/Wifi';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import HubIcon from '@mui/icons-material/Hub';

function formatBytes(bytes: number): string {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i];
}

function formatDuration(seconds: number): string {
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;

    if (hours > 0) {
        return `${hours}:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
    }
    return `${minutes}:${secs.toString().padStart(2, '0')}`;
}

function getStatusColor(status: string): string {
    switch (status.toLowerCase()) {
        case 'syncing':
            return 'var(--primary)'; // Blue
        case 'handshake':
            return '#f59e0b'; // Amber
        case 'idle':
            return 'var(--text-muted)'; // Gray
        default:
            return 'var(--text-muted)';
    }
}

interface GroupedConnection {
    device_id: string;
    user_name?: string;
    ip_address: string;
    status: string; // Aggregate status
    photos_uploaded: number;
    bytes_transferred: number;
    duration_seconds: number; // Max duration
    session_count: number;
}

export default function LiveConnections() {
    const { connections, loading, error } = useConnections(3000);

    if (loading && connections.length === 0) {
        return null;
    }

    if (error) {
        return null;
    }

    if (connections.length === 0) {
        return null;
    }

    // Group connections by device_id
    const groupedConnections: GroupedConnection[] = [];
    const deviceMap = new Map<string, GroupedConnection>();

    connections.forEach(conn => {
        if (!deviceMap.has(conn.device_id)) {
            deviceMap.set(conn.device_id, {
                device_id: conn.device_id,
                user_name: conn.user_name,
                ip_address: conn.ip_address,
                status: conn.status,
                photos_uploaded: 0,
                bytes_transferred: 0,
                duration_seconds: 0,
                session_count: 0
            });
        }

        const group = deviceMap.get(conn.device_id)!;
        group.photos_uploaded += conn.photos_uploaded;
        group.bytes_transferred += conn.bytes_transferred;
        group.session_count++;
        // Use max duration
        if (conn.duration_seconds > group.duration_seconds) {
            group.duration_seconds = conn.duration_seconds;
        }
        // If any session is syncing, group is syncing
        if (conn.status.toLowerCase() === 'syncing') {
            group.status = 'syncing';
        }
        // Update user_name if we found one and didn't have one before
        if (conn.user_name && !group.user_name) {
            group.user_name = conn.user_name;
        }
    });

    const devices = Array.from(deviceMap.values());

    return (
        <Box>
            <Box display="flex" alignItems="center" mb={2}>
                <Box
                    sx={{
                        width: 8,
                        height: 8,
                        borderRadius: '50%',
                        bgcolor: 'var(--accent-green)',
                        mr: 1.5,
                        boxShadow: '0 0 8px var(--accent-green)'
                    }}
                />
                <Typography variant="h6" fontWeight="bold">
                    Live Connections
                </Typography>
                <Chip
                    label={`${devices.length} Active Devices`}
                    size="small"
                    sx={{
                        ml: 2,
                        bgcolor: 'rgba(16, 185, 129, 0.1)',
                        color: 'var(--accent-green)',
                        fontWeight: 600
                    }}
                />
            </Box>

            <Grid container spacing={2}>
                {devices.map((device) => (
                    <Grid item xs={12} md={6} lg={4} key={device.device_id}>
                        <LiveConnectionCard group={device} />
                    </Grid>
                ))}
            </Grid>
        </Box>
    );
}

function LiveConnectionCard({ group }: { group: GroupedConnection }) {
    const isSyncing = group.status.toLowerCase() === 'syncing';
    const statusColor = getStatusColor(group.status);

    return (
        <Box
            className="glass-panel"
            sx={{
                p: 2,
                borderRadius: '16px',
                position: 'relative',
                overflow: 'hidden',
                transition: 'all 0.3s ease',
                '&:hover': {
                    transform: 'translateY(-2px)',
                    boxShadow: '0 8px 24px rgba(0,0,0,0.1)'
                }
            }}
        >
            {/* Animating Background Glow if Syncing */}
            {isSyncing && (
                <Box sx={{
                    position: 'absolute',
                    top: 0, left: 0, right: 0, bottom: 0,
                    background: `linear-gradient(45deg, transparent, ${statusColor}11, transparent)`,
                    animation: 'pulse 2s infinite',
                    zIndex: 0
                }} />
            )}

            <Box position="relative" zIndex={1}>
                {/* Header */}
                <Box mb={2}>
                    <Box display="flex" alignItems="center" gap={1.5} mb={1.5}>
                        <Box sx={{
                            p: 1,
                            borderRadius: '10px',
                            bgcolor: 'rgba(255,255,255,0.05)',
                            color: 'var(--text-primary)'
                        }}>
                            <SmartphoneIcon />
                        </Box>
                        <Box>
                            <Typography variant="subtitle1" fontWeight="bold" lineHeight={1.2}>
                                {group.user_name || group.device_id}
                            </Typography>
                            <Box display="flex" alignItems="center" gap={1}>
                                <Typography variant="caption" color="text.secondary">
                                    {group.user_name ? group.device_id : group.ip_address}
                                </Typography>
                                {group.session_count > 1 && (
                                    <Chip
                                        icon={<HubIcon style={{ fontSize: 12, color: 'inherit' }} />}
                                        label={`${group.session_count} Sessions`}
                                        size="small"
                                        sx={{
                                            height: 16,
                                            fontSize: '0.65rem',
                                            bgcolor: 'rgba(255,255,255,0.1)',
                                            color: 'text.secondary',
                                            '& .MuiChip-label': { px: 1 }
                                        }}
                                    />
                                )}
                            </Box>
                        </Box>
                    </Box>

                    {/* Status Row */}
                    <Box display="flex" justifyContent="space-between" alignItems="center"
                        sx={{
                            bgcolor: 'rgba(255,255,255,0.03)',
                            borderRadius: '8px',
                            px: 1.5,
                            py: 0.5
                        }}>
                        <Typography
                            variant="caption"
                            sx={{
                                color: statusColor,
                                fontWeight: 700,
                                textTransform: 'uppercase',
                                letterSpacing: '0.5px'
                            }}
                        >
                            {group.status}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                            {formatDuration(group.duration_seconds)}
                        </Typography>
                    </Box>
                </Box>

                {/* Metrics */}
                <Box sx={{ bgcolor: 'rgba(0,0,0,0.2)', borderRadius: '12px', p: 1.5 }}>
                    <Grid container spacing={2} alignItems="center">
                        <Grid item xs={6}>
                            <Box display="flex" alignItems="center" gap={1}>
                                <SwapHorizIcon sx={{ fontSize: 16, color: 'var(--text-muted)' }} />
                                <Typography variant="caption" color="text.secondary">
                                    Transferred
                                </Typography>
                            </Box>
                            <Typography variant="body2" fontWeight="600" sx={{ ml: 3 }}>
                                {formatBytes(group.bytes_transferred)}
                            </Typography>
                        </Grid>
                        <Grid item xs={6}>
                            <Box display="flex" alignItems="center" gap={1}>
                                <WifiIcon sx={{ fontSize: 16, color: 'var(--text-muted)' }} />
                                <Typography variant="caption" color="text.secondary">
                                    Photos
                                </Typography>
                            </Box>
                            <Typography variant="body2" fontWeight="600" sx={{ ml: 3 }}>
                                {group.photos_uploaded}
                            </Typography>
                        </Grid>
                    </Grid>
                </Box>

                {/* Progress Bar (Visible if syncing) */}
                {isSyncing && (
                    <Box mt={2}>
                        <LinearProgress
                            variant="indeterminate"
                            sx={{
                                height: 4,
                                borderRadius: 2,
                                bgcolor: 'rgba(255,255,255,0.1)',
                                '& .MuiLinearProgress-bar': { bgcolor: statusColor }
                            }}
                        />
                    </Box>
                )}
            </Box>
        </Box>
    );
}
