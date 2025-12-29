import { useState, useCallback, useEffect } from 'react';
import { Box, Typography, Grid, Card, CardMedia, CardContent, TextField, Select, MenuItem, FormControl, InputLabel, Chip, CircularProgress, Skeleton } from '@mui/material';
import { api, MediaItem, Client } from '../services/api';
import { useInfiniteScroll } from '../hooks/useInfiniteScroll';
import MediaViewer from './MediaViewer';
import PhotoCameraIcon from '@mui/icons-material/PhotoCamera';

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
    const [clientFilter, setClientFilter] = useState<number>(-1);
    const [searchTerm, setSearchTerm] = useState('');
    const [selectedPhoto, setSelectedPhoto] = useState<MediaItem | null>(null);
    const [viewerOpen, setViewerOpen] = useState(false);
    const [clients, setClients] = useState<Client[]>([]);

    useEffect(() => {
        const fetchClients = async () => {
            try {
                const response = await api.getClients();
                // Check if response.data is the array or if it's wrapped in { clients: [] }
                // Based on ApiServer.cpp: json response = {{"clients", clientsJson}};
                // So response.data should have a .clients property?
                // Wait, let's verify ApiServer.cpp output format again.
                // ApiServer.cpp: json response = {{"clients", clientsJson}}; return response.dump();
                // Yes, it returns an object with "clients" key.
                // However, without fully typing the response of api.getClients in api.ts, I should be careful.
                // Let's assume response.data.clients if it exists, or response.data if it's an array.
                // Actually, let's just properly type it in api.ts or cast it here.
                const data = response.data as any;
                if (data.clients && Array.isArray(data.clients)) {
                    setClients(data.clients);
                } else if (Array.isArray(data)) {
                    setClients(data);
                }
            } catch (err) {
                console.error("Error fetching clients:", err);
            }
        };
        fetchClients();
    }, []);

    const fetchPhotos = useCallback(async (offset: number, limit: number) => {
        const response = await api.getMedia(offset, limit, clientFilter >= 0 ? clientFilter : undefined, undefined, undefined, searchTerm);
        return response.data;
    }, [clientFilter, searchTerm]);

    const {
        items: photos,
        loading,
        hasMore,
        error,
        sentinelRef,
        reset
    } = useInfiniteScroll<MediaItem>({
        fetchFunction: fetchPhotos,
        limit: 50
    });

    // Reset when filter changes
    const handleClientFilterChange = (value: number) => {
        setClientFilter(value);
        reset();
    };

    // Handle photo click to open viewer
    const handlePhotoClick = (photo: MediaItem) => {
        setSelectedPhoto(photo);
        setViewerOpen(true);
    };

    // Handle navigation in viewer
    const handleNavigate = (direction: 'prev' | 'next') => {
        if (!selectedPhoto) return;
        const currentIndex = filteredPhotos.findIndex(p => p.id === selectedPhoto.id);
        if (direction === 'prev' && currentIndex > 0) {
            setSelectedPhoto(filteredPhotos[currentIndex - 1]);
        } else if (direction === 'next' && currentIndex < filteredPhotos.length - 1) {
            setSelectedPhoto(filteredPhotos[currentIndex + 1]);
        }
    };

    // Server-side search implemented
    const filteredPhotos = photos;

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
                    onChange={(e) => {
                        setSearchTerm(e.target.value);
                        // Debounce logic would be better, but for now simple reset is okay
                        // Actually, we shouldn't reset on every keystroke for infinite scroll if we don't debounce
                        // But let's keep it simple: We'll modify useEffect to reset when searchTerm changes
                    }}
                    onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                            reset();
                        }
                    }}
                    onBlur={() => reset()} // Fetch when focus lost
                    sx={{ minWidth: 250 }}
                />

                <FormControl size="small" sx={{ minWidth: 150 }}>
                    <InputLabel>Client</InputLabel>
                    <Select
                        value={clientFilter}
                        onChange={(e) => handleClientFilterChange(Number(e.target.value))}
                        label="Client"
                    >
                        <MenuItem value={-1}>All Clients</MenuItem>
                        {clients.map((client) => (
                            <MenuItem key={client.id} value={client.id}>
                                {client.name}
                            </MenuItem>
                        ))}
                    </Select>
                </FormControl>
            </Box>

            {error && (
                <Box mb={2}>
                    <Typography color="error">{error}</Typography>
                </Box>
            )}

            {/* Photo Grid */}
            <Grid container spacing={2}>
                {filteredPhotos.map((photo) => (
                    <Grid item xs={12} sm={6} md={4} lg={3} key={photo.id}>
                        <Card
                            className="glass-panel"
                            onClick={() => handlePhotoClick(photo)}
                            sx={{ cursor: 'pointer', transition: 'transform 0.2s', '&:hover': { transform: 'scale(1.02)' } }}
                        >
                            <CardMedia
                                component="img"
                                height="200"
                                image={photo.thumbnailUrl}
                                alt={photo.filename}
                                sx={{
                                    objectFit: 'cover',
                                    backgroundColor: 'rgba(255, 255, 255, 0.05)',
                                }}
                                onError={(e: any) => {
                                    e.target.style.display = 'none';
                                    e.target.nextSibling.style.display = 'flex';
                                }}
                            />
                            <Box
                                sx={{
                                    height: 200,
                                    backgroundColor: 'rgba(255, 255, 255, 0.1)',
                                    display: 'none',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                }}
                            >
                                <PhotoCameraIcon sx={{ fontSize: 60, opacity: 0.3 }} />
                            </Box>
                            <CardContent>
                                <Typography variant="body2" fontWeight={500} noWrap>
                                    {photo.filename}
                                </Typography>
                                <Typography variant="caption" color="text.secondary" display="block">
                                    {formatBytes(photo.size)}
                                </Typography>
                                <Box mt={1} display="flex" gap={0.5} flexWrap="wrap" alignItems="center">
                                    <Chip label={`Client ${photo.clientId}`} size="small" color="primary" variant="outlined" />
                                    <Typography variant="caption" color="text.secondary">
                                        {formatTimeAgo(photo.uploadedAt)}
                                    </Typography>
                                </Box>
                            </CardContent>
                        </Card>
                    </Grid>
                ))}

                {/* Loading skeletons */}
                {loading && Array.from({ length: 8 }).map((_, i) => (
                    <Grid item xs={12} sm={6} md={4} lg={3} key={`skeleton-${i}`}>
                        <Card>
                            <Skeleton variant="rectangular" height={200} />
                            <CardContent>
                                <Skeleton variant="text" width="80%" />
                                <Skeleton variant="text" width="40%" />
                                <Skeleton variant="text" width="60%" />
                            </CardContent>
                        </Card>
                    </Grid>
                ))}
            </Grid>

            {/* Sentinel element for infinite scroll */}
            {hasMore && !loading && (
                <div ref={sentinelRef} style={{ height: '20px', margin: '20px 0' }} />
            )}

            {/* No photos message */}
            {!loading && filteredPhotos.length === 0 && (
                <Box textAlign="center" py={4}>
                    <Typography color="text.secondary">
                        {photos.length === 0 ? 'No photos found' : 'No photos match your search'}
                    </Typography>
                </Box>
            )}

            {/* End of list message */}
            {!hasMore && photos.length > 0 && (
                <Box textAlign="center" py={2}>
                    <Typography variant="caption" color="text.secondary">
                        All photos loaded ({photos.length} total)
                    </Typography>
                </Box>
            )}

            {/* Media Viewer Modal */}
            <MediaViewer
                open={viewerOpen}
                photo={selectedPhoto}
                photos={filteredPhotos}
                onClose={() => setViewerOpen(false)}
                onNavigate={handleNavigate}
            />
        </Box>
    );
}
