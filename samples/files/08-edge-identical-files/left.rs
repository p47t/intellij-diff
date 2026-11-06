pub struct RingBuffer<T> {
    data: Vec<Option<T>>,
    head: usize,
    tail: usize,
}

impl<T> RingBuffer<T> {
    pub fn with_capacity(cap: usize) -> Self {
        Self {
            data: (0..cap).map(|_| None).collect(),
            head: 0,
            tail: 0,
        }
    }
}
