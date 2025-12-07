import { useEffect, useCallback } from 'react';
import { Dialog, IconButton, Box, CircularProgress, Typography } from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import { MediaItem } from '../services/api';

interface MediaViewerProps {
    open: boolean;
    photo: MediaItem | null;
    photos: MediaItem[];
    onClose: () => void;
    onNavigate?: (direction: 'prev' | 'next') => void;
}

export default function MediaViewer({ open, photo, photos, onClose, onNavigate }: MediaViewerProps) {
    const currentIndex = photo ? photos.findIndex(p => p.id === photo.id) : -1;
    const hasPrev = currentIndex > 0;
    const hasNext = currentIndex < photos.length - 1;

    const handlePrev = useCallback(() => {
        if (hasPrev && onNavigate) {
            onNavigate('prev');
        }
    }, [hasPrev, onNavigate]);

    const handleNext = useCallback(() => {
        if (hasNext && onNavigate) {
            onNavigate('next');
        }
    }, [hasNext, onNavigate]);

    // Keyboard shortcuts
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if (!open) return;

            switch (e.key) {
                case 'Escape':
                    onClose();
                    break;
                case 'ArrowLeft':
                    handlePrev();
                    break;
                case 'ArrowRight':
                    handleNext();
                    break;
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [open, onClose, handlePrev, handleNext]);

    if (!photo) return null;

    return (
        <Dialog
            open={open}
            onClose={onClose}
            maxWidth={false}
            PaperProps={{
                sx: {
                    backgroundColor: 'rgba(0, 0, 0, 0.95)',
                    boxShadow: 'none',
                    margin: 0,
                    maxHeight: '100vh',
                    maxWidth: '100vw',
                },
            }}
        >
            <Box
                sx={{
                    position: 'relative',
                    width: '100vw',
                    height: '100vh',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    backgroundColor: 'rgba(0, 0, 0, 0.95)',
                }}
            >
                {/* Close button */}
                <IconButton
                    onClick={onClose}
                    sx={{
                        position: 'absolute',
                        top: 16,
                        right: 16,
                        color: 'white',
                        backgroundColor: 'rgba(255, 255, 255, 0.1)',
                        '&:hover': {
                            backgroundColor: 'rgba(255, 255, 255, 0.2)',
                        },
                        zIndex: 2,
                    }}
                >
                    <CloseIcon />
                </IconButton>

                {/* Previous button */}
                {hasPrev && (
                    <IconButton
                        onClick={handlePrev}
                        sx={{
                            position: 'absolute',
                            left: 16,
                            color: 'white',
                            backgroundColor: 'rgba(255, 255, 255, 0.1)',
                            '&:hover': {
                                backgroundColor: 'rgba(255, 255, 255, 0.2)',
                            },
                            zIndex: 2,
                        }}
                    >
                        <ArrowBackIcon />
                    </IconButton>
                )}

                {/* Next button */}
                {hasNext && (
                    <IconButton
                        onClick={handleNext}
                        sx={{
                            position: 'absolute',
                            right: 16,
                            color: 'white',
                            backgroundColor: 'rgba(255, 255, 255, 0.1)',
                            '&:hover': {
                                backgroundColor: 'rgba(255, 255, 255, 0.2)',
                            },
                            zIndex: 2,
                        }}
                    >
                        <ArrowForwardIcon />
                    </IconButton>
                )}

                {/* Image */}
                <Box
                    sx={{
                        maxWidth: '90%',
                        maxHeight: '90%',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                    }}
                >
                    <img
                        src={photo.fullUrl}
                        alt={photo.filename}
                        style={{
                            maxWidth: '100%',
                            maxHeight: '90vh',
                            objectFit: 'contain',
                            borderRadius: '8px',
                        }}
                        onError={(e: any) => {
                            e.target.style.display = 'none';
                            e.target.nextSibling.style.display = 'flex';
                        }}
                    />
                    <Box
                        sx={{
                            display: 'none',
                            flexDirection: 'column',
                            alignItems: 'center',
                            gap: 2,
                            color: 'white',
                        }}
                    >
                        <CircularProgress sx={{ color: 'white' }} />
                        <Typography>Failed to load image</Typography>
                    </Box>
                </Box>

                {/* Image info */}
                <Box
                    sx={{
                        position: 'absolute',
                        bottom: 16,
                        left: '50%',
                        transform: 'translateX(-50%)',
                        backgroundColor: 'rgba(0, 0, 0, 0.7)',
                        padding: '8px 16px',
                        borderRadius: '8px',
                        color: 'white',
                        textAlign: 'center',
                        zIndex: 2,
                    }}
                >
                    <Typography variant="body2" fontWeight={500}>
                        {photo.filename}
                    </Typography>
                    <Typography variant="caption" color="rgba(255, 255, 255, 0.7)">
                        {currentIndex + 1} / {photos.length}
                    </Typography>
                </Box>
            </Box>
        </Dialog>
    );
}
