import React, { useEffect, useState } from 'react';
import {
    Box,
    Card,
    CardContent,
    Container,
    Grid,
    Typography,
    LinearProgress,
    Chip,
    Alert,
} from '@mui/material';
import {
    Storage as StorageIcon,
    Schedule as ScheduleIcon,
    Dns as DnsIcon,
    Warning as WarningIcon,
} from '@mui/icons-material';
import axios from 'axios';

interface HealthData {
    uptime: number;
    storage: {
        used: number;
        limit: number;
        utilizationPercent: number;
        trend: any[];
    };
    queue: {
        thumbnailGeneration: number;
        sessionCleanup: number;
        logCleanup: number;
    };
    system: {
        cpu: any;
        ram: any;
        note: string;
    };
    lastUpdated: string;
}

const ServerHealth: React.FC = () => {
    const [data, setData] = useState<HealthData | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [loading, setLoading] = useState<boolean>(true);

    const fetchHealth = async () => {
        try {
            const response = await axios.get('/api/health');
            setData(response.data);
            setError(null);
        } catch (err) {
            setError('Failed to fetch server health metrics');
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchHealth();
        const interval = setInterval(fetchHealth, 30000);
        return () => clearInterval(interval);
    }, []);

    const formatBytes = (bytes: number) => {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    };

    const formatUptime = (seconds: number) => {
        if (seconds === 0) return 'Just started';
        const d = Math.floor(seconds / (3600 * 24));
        const h = Math.floor((seconds % (3600 * 24)) / 3600);
        const m = Math.floor((seconds % 3600) / 60);
        const s = Math.floor(seconds % 60);

        const parts = [];
        if (d > 0) parts.push(`${d}d`);
        if (h > 0) parts.push(`${h}h`);
        if (m > 0) parts.push(`${m}m`);
        parts.push(`${s}s`);
        return parts.join(' ');
    };

    if (loading) {
        return <LinearProgress />;
    }

    if (error) {
        return (
            <Container maxWidth="lg" sx={{ mt: 4, mb: 4 }}>
                <Alert severity="error">{error}</Alert>
            </Container>
        );
    }

    return (
        <Container maxWidth="lg" sx={{ mt: 4, mb: 4 }}>
            <Typography variant="h4" gutterBottom component="div" sx={{ mb: 3 }}>
                Server Health
            </Typography>

            <Grid container spacing={3}>
                {/* Storage Card */}
                <Grid item xs={12} md={6}>
                    <Card>
                        <CardContent>
                            <Box display="flex" alignItems="center" mb={2}>
                                <StorageIcon color="primary" sx={{ mr: 1 }} />
                                <Typography variant="h6">Storage Usage</Typography>
                            </Box>

                            <Typography variant="h3" component="div" sx={{ mb: 1 }}>
                                {data?.storage.utilizationPercent.toFixed(1)}%
                            </Typography>

                            <LinearProgress
                                variant="determinate"
                                value={Math.min(data?.storage.utilizationPercent || 0, 100)}
                                sx={{ height: 10, borderRadius: 5, mb: 2 }}
                                color={data && data.storage.utilizationPercent > 90 ? 'error' : 'primary'}
                            />

                            <Box display="flex" justifyContent="space-between">
                                <Typography variant="body2" color="text.secondary">
                                    Used: {formatBytes(data?.storage.used || 0)}
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                    Limit: {formatBytes(data?.storage.limit || 0)}
                                </Typography>
                            </Box>
                        </CardContent>
                    </Card>
                </Grid>

                {/* System Status Card */}
                <Grid item xs={12} md={6}>
                    <Card>
                        <CardContent>
                            <Box display="flex" alignItems="center" mb={2}>
                                <DnsIcon color="primary" sx={{ mr: 1 }} />
                                <Typography variant="h6">System Status</Typography>
                            </Box>

                            <Grid container spacing={2}>
                                <Grid item xs={6}>
                                    <Typography variant="subtitle2" color="text.secondary">
                                        Uptime
                                    </Typography>
                                    <Typography variant="h6">
                                        {formatUptime(data?.uptime || 0)}
                                    </Typography>
                                </Grid>
                                <Grid item xs={6}>
                                    <Typography variant="subtitle2" color="text.secondary">
                                        Last Updated
                                    </Typography>
                                    <Typography variant="body2">
                                        {data?.lastUpdated}
                                    </Typography>
                                </Grid>
                            </Grid>

                            <Box mt={2} p={1} bgcolor="#f5f5f5" borderRadius={1}>
                                <Typography variant="caption" color="text.secondary">
                                    {data?.system.note}
                                </Typography>
                            </Box>
                        </CardContent>
                    </Card>
                </Grid>

                {/* Queue Metrics */}
                <Grid item xs={12}>
                    <Card>
                        <CardContent>
                            <Box display="flex" alignItems="center" mb={2}>
                                <ScheduleIcon color="primary" sx={{ mr: 1 }} />
                                <Typography variant="h6">Background Jobs</Typography>
                            </Box>

                            <Grid container spacing={2}>
                                <Grid item xs={4}>
                                    <Box textAlign="center" p={2} border={1} borderColor="grey.200" borderRadius={2}>
                                        <Typography variant="h4" color="primary">
                                            {data?.queue.thumbnailGeneration}
                                        </Typography>
                                        <Typography variant="body2" color="text.secondary">
                                            Thumbnail Generation
                                        </Typography>
                                    </Box>
                                </Grid>
                                <Grid item xs={4}>
                                    <Box textAlign="center" p={2} border={1} borderColor="grey.200" borderRadius={2}>
                                        <Typography variant="h4" color="primary">
                                            {data?.queue.sessionCleanup}
                                        </Typography>
                                        <Typography variant="body2" color="text.secondary">
                                            Session Cleanup
                                        </Typography>
                                    </Box>
                                </Grid>
                                <Grid item xs={4}>
                                    <Box textAlign="center" p={2} border={1} borderColor="grey.200" borderRadius={2}>
                                        <Typography variant="h4" color="primary">
                                            {data?.queue.logCleanup}
                                        </Typography>
                                        <Typography variant="body2" color="text.secondary">
                                            Log Cleanup
                                        </Typography>
                                    </Box>
                                </Grid>
                            </Grid>
                        </CardContent>
                    </Card>
                </Grid>
            </Grid>
        </Container>
    );
};

export default ServerHealth;
