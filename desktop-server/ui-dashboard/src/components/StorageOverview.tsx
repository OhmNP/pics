import React, { useEffect, useState } from 'react';
import {
    Box,
    Card,
    CardContent,
    Container,
    Grid,
    Typography,
    LinearProgress,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Paper,
} from '@mui/material';
import {
    PieChart,
    Pie,
    Cell,
    ResponsiveContainer,
    Tooltip,
    Legend,
    BarChart,
    Bar,
    XAxis,
    YAxis,
    CartesianGrid
} from 'recharts';
import axios from 'axios';

interface StorageStats {
    totalStorageUsed: number;
    totalFiles: number;
    storageLimit: number;
    utilizationPercent: number;
    byClient: { clientId: number; storageUsed: number }[];
    byFileType: { [key: string]: number };
}

const COLORS = ['#0088FE', '#00C49F', '#FFBB28', '#FF8042', '#AF19FF', '#FF1919'];

const formatBytes = (bytes: number) => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};

const StorageOverview: React.FC = () => {
    const [stats, setStats] = useState<StorageStats | null>(null);
    const [loading, setLoading] = useState<boolean>(true);

    useEffect(() => {
        const fetchStats = async () => {
            try {
                const response = await axios.get('/api/storage/overview');
                setStats(response.data);
            } catch (err) {
                console.error('Failed to fetch storage stats', err);
            } finally {
                setLoading(false);
            }
        };

        fetchStats();
    }, []);

    if (loading) return <LinearProgress />;

    if (!stats) return <Typography>No data available</Typography>;

    const pieData = Object.entries(stats.byFileType).map(([name, value]) => ({
        name,
        value,
    }));

    // Prepare client data for bar chart
    // Fetch client names? For now just use ID.
    const barData = stats.byClient.map(c => ({
        name: `Client ${c.clientId}`,
        value: c.storageUsed // in bytes. Chart might need formatting.
    }));

    return (
        <Container maxWidth="lg" sx={{ mt: 4, mb: 4 }}>
            <Typography variant="h4" gutterBottom>
                Storage Overview
            </Typography>

            <Grid container spacing={3}>
                {/* Summary Cards */}
                <Grid item xs={12} md={4}>
                    <Card>
                        <CardContent>
                            <Typography color="textSecondary" gutterBottom>
                                Total Used
                            </Typography>
                            <Typography variant="h4">
                                {formatBytes(stats.totalStorageUsed)}
                            </Typography>
                            <LinearProgress
                                variant="determinate"
                                value={Math.min(stats.utilizationPercent, 100)}
                                sx={{ mt: 2 }}
                            />
                            <Typography variant="caption" display="block" sx={{ mt: 1 }}>
                                {stats.utilizationPercent.toFixed(1)}% of {formatBytes(stats.storageLimit)}
                            </Typography>
                        </CardContent>
                    </Card>
                </Grid>

                <Grid item xs={12} md={4}>
                    <Card>
                        <CardContent>
                            <Typography color="textSecondary" gutterBottom>
                                Total Files
                            </Typography>
                            <Typography variant="h4">
                                {stats.totalFiles.toLocaleString()}
                            </Typography>
                        </CardContent>
                    </Card>
                </Grid>

                <Grid item xs={12} md={4}>
                    <Card>
                        <CardContent>
                            <Typography color="textSecondary" gutterBottom>
                                File Types
                            </Typography>
                            <Typography variant="h4">
                                {Object.keys(stats.byFileType).length}
                            </Typography>
                        </CardContent>
                    </Card>
                </Grid>

                {/* Charts */}
                <Grid item xs={12} md={6}>
                    <Card sx={{ height: 400 }}>
                        <CardContent sx={{ height: '100%' }}>
                            <Typography variant="h6" gutterBottom>
                                Storage by File Type
                            </Typography>
                            <ResponsiveContainer width="100%" height="90%">
                                <PieChart>
                                    <Pie
                                        data={pieData}
                                        cx="50%"
                                        cy="50%"
                                        labelLine={false}
                                        outerRadius={80}
                                        fill="#8884d8"
                                        dataKey="value"
                                        label={({ name, percent }) => `${name} ${(percent * 100).toFixed(0)}%`}
                                    >
                                        {pieData.map((entry, index) => (
                                            <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                                        ))}
                                    </Pie>
                                    <Tooltip formatter={(value: number) => formatBytes(value)} />
                                    <Legend />
                                </PieChart>
                            </ResponsiveContainer>
                        </CardContent>
                    </Card>
                </Grid>

                <Grid item xs={12} md={6}>
                    <Card sx={{ height: 400 }}>
                        <CardContent sx={{ height: '100%' }}>
                            <Typography variant="h6" gutterBottom>
                                Storage by Client
                            </Typography>
                            <ResponsiveContainer width="100%" height="90%">
                                <BarChart data={barData}>
                                    <CartesianGrid strokeDasharray="3 3" />
                                    <XAxis dataKey="name" />
                                    <YAxis tickFormatter={formatBytes} />
                                    <Tooltip formatter={(value: number) => formatBytes(value)} />
                                    <Legend />
                                    <Bar dataKey="value" name="Storage Used" fill="#8884d8" />
                                </BarChart>
                            </ResponsiveContainer>
                        </CardContent>
                    </Card>
                </Grid>
            </Grid>
        </Container>
    );
};

export default StorageOverview;
