import { useState, useEffect } from 'react';
import { Box, Typography, Grid, Card, CardContent, CardMedia, TextField, Select, MenuItem, FormControl, InputLabel, Chip, CircularProgress } from '@mui/material';
import { api } from '../services/api';
import PhotoCameraIcon from '@mui/icons-material/PhotoCamera';

interface Photo {
    id: number;
    filename: string;
    size: number;
    clientId: number;
    clientName: string;
    receivedAt: string;
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
    if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
    if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
    return `${Math.floor(seconds / 86400)}d ago`;
}

export default function Photos() {
    const [photos, setPhotos] = useState<Photo[]>([]);
    const [loading, setLoading] = useState(true);
    const [searchTerm, setSearchTerm] = useState('');
    const [clientFilter, setClientFilter] = useState('all');
    const [sortBy, setSortBy] = useState('newest');

    useEffect(() => {
        const fetchPhotos = async () => {
            try {
                const response = await api.getPhotos();
                // Note: API currently returns empty array, using mock data for UI demo
                setPhotos([
                    { id: 1, filename: 'IMG_001.jpg', size: 2100000, clientId: 1, clientName: 'Pixel 7', receivedAt: new Date(Date.now() - 7200000).toISOString() },
                    { id: 2, filename: 'IMG_002.jpg', size: 1800000, clientId: 1, clientName: 'Pixel 7', receivedAt: new Date(Date.now() - 14400000).toISOString() },
                    { id: 3, filename: 'IMG_003.jpg', size: 2500000, clientId: 2, clientName: 'Galaxy S21', receivedAt: new Date(Date.now() - 21600000).toISOString() },
                    { id: 4, filename: 'IMG_004.jpg', size: 1900000, clientId: 1, clientName: 'Pixel 7', receivedAt: new Date(Date.now() - 28800000).toISOString() },
                ]);
            } catch (err) {
                console.error('Error fetching photos:', err);
            } finally {
                setLoading(false);
            }
        };

        fetchPhotos();
    }, []);

    const filteredPhotos = photos.filter(photo => {
        const matchesSearch = photo.filename.toLowerCase().includes(searchTerm.toLowerCase());
        const matchesClient = clientFilter === 'all' || photo.clientId.toString() === clientFilter;
        return matchesSearch && matchesClient;
    });

    if (loading) {
        return (
            <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
                <CircularProgress />
            </Box>
        );
    }

    return (
        <Box p={3}>
            <Typography variant="h4" fontWeight="bold" gutterBottom>
                Photos
            </Typography>

            {/* Filters */}
            <Box display="flex" gap={2} mb={3} flexWrap="wrap">
                <TextField
                    label="Search photos"
                    variant="outlined"
                    size="small"
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                    sx={{ minWidth: 250 }}
                />

                <FormControl size="small" sx={{ minWidth: 150 }}>
                    <InputLabel>Client</InputLabel>
                    <Select value={clientFilter} onChange={(e) => setClientFilter(e.target.value)} label="Client">
                        <MenuItem value="all">All Clients</MenuItem>
                        <MenuItem value="1">Pixel 7</MenuItem>
                        <MenuItem value="2">Galaxy S21</MenuItem>
                    </Select>
                </FormControl>

                <FormControl size="small" sx={{ minWidth: 150 }}>
                    <InputLabel>Sort</InputLabel>
                    <Select value={sortBy} onChange={(e) => setSortBy(e.target.value)} label="Sort">
                        <MenuItem value="newest">Newest First</MenuItem>
                        <MenuItem value="oldest">Oldest First</MenuItem>
                        <MenuItem value="largest">Largest First</MenuItem>
                    </Select>
                </FormControl>
            </Box>

            {/* Photo Grid */}
            <Grid container spacing={2}>
                {filteredPhotos.map((photo) => (
                    <Grid item xs={12} sm={6} md={4} lg={3} key={photo.id}>
                        <Card>
                            <CardMedia
                                sx={{
                                    height: 200,
                                    backgroundColor: 'rgba(255, 255, 255, 0.1)',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                }}
                            >
                                <PhotoCameraIcon sx={{ fontSize: 60, opacity: 0.3 }} />
                            </CardMedia>
                            <CardContent>
                                <Typography variant="body2" fontWeight={500} noWrap>
                                    {photo.filename}
                                </Typography>
                                <Typography variant="caption" color="text.secondary" display="block">
                                    {formatBytes(photo.size)}
                                </Typography>
                                <Box mt={1} display="flex" gap={0.5} flexWrap="wrap" alignItems="center">
                                    <Chip label={photo.clientName} size="small" color="primary" variant="outlined" />
                                    <Typography variant="caption" color="text.secondary">
                                        {formatTimeAgo(photo.receivedAt)}
                                    </Typography>
                                </Box>
                            </CardContent>
                        </Card>
                    </Grid>
                ))}
            </Grid>

            {filteredPhotos.length === 0 && (
                <Box textAlign="center" py={4}>
                    <Typography color="text.secondary">No photos found</Typography>
                </Box>
            )}
        </Box>
    );
}
