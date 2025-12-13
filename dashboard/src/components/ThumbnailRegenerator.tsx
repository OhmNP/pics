
import React, { useState } from 'react';
import {
    Box,
    Button,
    Card,
    CardContent,
    Typography,
    Alert,
    CircularProgress
} from '@mui/material';
import {
    Image as ImageIcon,
    Refresh as RefreshIcon
} from '@mui/icons-material';
import axios from 'axios';

export const ThumbnailRegenerator: React.FC = () => {
    const [loading, setLoading] = useState(false);
    const [message, setMessage] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);

    const handleRegenerate = async () => {
        if (!confirm("Are you sure you want to regenerate ALL thumbnails? This may take some time.")) {
            return;
        }

        setLoading(true);
        setMessage(null);
        setError(null);

        try {
            const token = localStorage.getItem('auth_token');
            const response = await axios.post('/api/maintenance/thumbnails',
                { all: true },
                { headers: { Authorization: `Bearer ${token}` } }
            );

            if (response.data.success) {
                setMessage(response.data.message);
            } else {
                setError(response.data.error || "Operation failed");
            }
        } catch (err: any) {
            setError(err.response?.data?.error || err.message || "Request failed");
        } finally {
            setLoading(false);
        }
    };

    return (
        <Card sx={{ mt: 3 }}>
            <CardContent>
                <Box display="flex" alignItems="center" gap={2} mb={2}>
                    <ImageIcon color="primary" />
                    <Typography variant="h6">Thumbnail Maintenance</Typography>
                </Box>

                <Typography variant="body2" color="text.secondary" paragraph>
                    If you see missing or broken thumbnails in the Media Grid, you can clear the thumbnail cache to force regeneration.
                </Typography>

                {message && <Alert severity="success" sx={{ mb: 2 }}>{message}</Alert>}
                {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

                <Button
                    variant="outlined"
                    color="warning"
                    startIcon={loading ? <CircularProgress size={20} /> : <RefreshIcon />}
                    onClick={handleRegenerate}
                    disabled={loading}
                >
                    {loading ? 'Clearing Cache...' : 'Clear Thumbnail Cache'}
                </Button>
            </CardContent>
        </Card>
    );
};
