import { useState, useEffect, useRef, useCallback } from 'react';

interface UseInfiniteScrollOptions<T> {
    fetchFunction: (offset: number, limit: number) => Promise<{
        items: T[];
        pagination: {
            offset: number;
            limit: number;
            total: number;
            hasMore: boolean;
        };
    }>;
    limit?: number;
}

interface UseInfiniteScrollReturn<T> {
    items: T[];
    loading: boolean;
    hasMore: boolean;
    error: string | null;
    loadMore: () => void;
    sentinelRef: (node: HTMLDivElement | null) => void;
    reset: () => void;
}

export function useInfiniteScroll<T>({
    fetchFunction,
    limit = 50,
}: UseInfiniteScrollOptions<T>): UseInfiniteScrollReturn<T> {
    const [items, setItems] = useState<T[]>([]);
    const [offset, setOffset] = useState(0);
    const [loading, setLoading] = useState(false);
    const [hasMore, setHasMore] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const observer = useRef<IntersectionObserver | null>(null);

    const loadMore = useCallback(async () => {
        if (loading || !hasMore) return;

        setLoading(true);
        setError(null);

        try {
            const response = await fetchFunction(offset, limit);

            setItems((prev) => [...prev, ...response.items]);
            setOffset((prev) => prev + limit);
            setHasMore(response.pagination.hasMore);
        } catch (err: any) {
            setError(err.message || 'Failed to load items');
            console.error('Error loading items:', err);
        } finally {
            setLoading(false);
        }
    }, [fetchFunction, offset, limit, loading, hasMore]);

    const sentinelRef = useCallback(
        (node: HTMLDivElement | null) => {
            if (loading) return;
            if (observer.current) observer.current.disconnect();

            observer.current = new IntersectionObserver((entries) => {
                if (entries[0].isIntersecting && hasMore) {
                    loadMore();
                }
            });

            if (node) observer.current.observe(node);
        },
        [loading, hasMore, loadMore]
    );

    const reset = useCallback(() => {
        setItems([]);
        setOffset(0);
        setHasMore(true);
        setError(null);
    }, []);

    // Load initial items
    useEffect(() => {
        if (items.length === 0 && !loading && hasMore) {
            loadMore();
        }
    }, []);

    return {
        items,
        loading,
        hasMore,
        error,
        loadMore,
        sentinelRef,
        reset,
    };
}
