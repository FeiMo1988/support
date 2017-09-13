package david.support.views;

import android.content.Context;
import android.database.DataSetObserver;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.OverScroller;

import java.util.LinkedList;
import java.util.List;

/**
 * @author David Chen (dingwei.chen1988@gmail.com)
 */

public class LoopPlayerLayout extends AdapterView<LoopPlayerLayout.LoopPlayAdapter> {

    private LoopPlayerLayout.LoopPlayAdapter mBaseAdapter = null;
    private DataSetObserverImpl mDataSetObserverImpl = new DataSetObserverImpl();
    private PlayAnimationAction mPlayAnimationAction;
    private static final int MIN_OFFSET = 10;
    private static final int UNDEFINED = Integer.MIN_VALUE;
    private static final int ANIMATION_TIME = 500;
//    private static final int FIRST_INDEX = -1;
    private static final int WAIT_TIME = 1500;
    private int mAnimationTime = ANIMATION_TIME;
    private int mWaitTime = WAIT_TIME;
    private int mCurrentIndex = 0;
    private Direction mDirection = Direction.HORIZONTAL;
    private List<View> mRecyclerBin = new LinkedList<>();
    private Item mPre = new Item();
    private Item mCurrent = new Item();
    private Item mNext = new Item();
    private Item[] mAllItems = new Item[]{mPre, mCurrent, mNext};
    private static final int MAX_RECYCLER_NUMBER = 3;
    private LayoutParams mLayoutParams = new LayoutParams(LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT);
    private OnLoopPlayListener mOnLoopPlayListener = null;
    private int mPreMotionX;
    private int mPreMotionY;
    private int mMotionX;
    private int mMotionY;
    private int mStartMotionX;
    private int mStartMotionY;
    private static final int DRAG_STATUS_NULL = 0x0;
    private static final int DRAG_STATUS_ENABLE = 0x01;
    private static final int DRAG_STATUS_DISABLE = 0x01 << 1;
    private int mDragStatus = DRAG_STATUS_NULL;
    private int mTouchSlop;
    private int mScrollOffset = 0;
    private int mFinalOffset = 0;

    private class Item {

        private View view;
        private int index = UNDEFINED;
        private int offset;
        private int renderIndex;

        private int getRenderIndex() {
            if (getDataCount() > 1) {
                if (index < 0) {
                    int render = index;
                    while (render < 0) {
                        render += getDataCount();
                    }
                    return render;
                }

                if (index >= getDataCount()) {
                    int render = index;
                    while (render >= getDataCount()) {
                        render -= getDataCount();
                    }
                    return render;
                }
            } else {
                if (index < 0 || index >= getDataCount()) {
                    return -1;
                }
            }
            return index;
        }

        public void onAnimationEnd() {
            if (this != mCurrent) {
                return;
            }
            if (renderIndex != this.index) {
                mCurrentIndex = renderIndex;
                this.updateIndexAndRelocation(this.renderIndex);
                mPre.updateIndexAndRelocation(this.index - 1);
                mNext.updateIndexAndRelocation(this.index + 1);
                scrollInternal(this.offset);
            }
            if (mOnLoopPlayListener != null) {
                mOnLoopPlayListener.onPositionChange(this.index, getDataCount());
            }
        }

        public void copyFrom(Item item) {
            this.view = item.view;
            this.index = item.index;
            this.renderIndex = item.renderIndex;
            this.offset = item.offset;
        }

        private void attachAndLayout(int i,boolean isLayout) {
            if (this.index != i) {
                this.index = i;
                this.renderIndex = getRenderIndex();
                if (renderIndex >= 0) {
                    this.view = obtainView(renderIndex);
                } else {
                    this.view = null;
                }
                if (view != null) {
                    attachView(view);
                    if (isLayout) {
                        this.layoutAndLocation();
                    }
                }
            }
        }

        private void layoutAndLocation() {
            this.offset(getItemSize() * this.index);
            this.layout();
            this.location();
        }

        private void updateIndexAndRelocation(int index) {
            if (this.index != index) {
                this.index = index;
                this.offset(getItemSize() * this.index);
                this.location();
            }
        }


        public void offset(int offset) {
            this.offset = offset;
        }

        private void location() {
            if (view != null) {
                int left = 0;
                int top = 0;
                if (mDirection == Direction.HORIZONTAL) {
                    left = this.offset + getPaddingLeft();
                    top = getPaddingTop();
                } else {
                    left = getPaddingLeft();
                    top = this.offset + getPaddingTop();
                }
                view.layout(left,
                        top,
                        left+view.getMeasuredWidth(),
                        top+view.getMeasuredHeight());
            }
        }

        public void layout() {
            int paddingV = getPaddingTop() + getPaddingBottom();
            int paddingH = getPaddingLeft() + getPaddingRight();
            if (view != null) {
                view.measure((getWidth() - paddingH) | MeasureSpec.EXACTLY,
                        (getHeight() - paddingV) | MeasureSpec.EXACTLY);
               // view.layout(0,0,view.getMeasuredWidth(),view.getMeasuredHeight());
            }
        }

        public void clear() {
            view = null;
            index = Integer.MIN_VALUE;
            renderIndex = -1;
        }

        public void recycle() {
            if (view != null) {
                recycleView(view);
                index = -1;
                if (view.getParent() != null) {
                    LoopPlayerLayout.this.removeViewInLayout(view);
                }
            }
        }
    }


    private void attachView(View view) {
        if (view.getParent() != this) {
            this.addViewInLayout(view, -1, mLayoutParams);
        }
    }


    private View obtainView(int position) {
        return mBaseAdapter.getView(position, obtainFromRecyclerBin(), this);
    }

    private View obtainFromRecyclerBin() {
        if (mRecyclerBin.size() > 0) {
            return mRecyclerBin.remove(0);
        }
        return null;
    }

    private int getItemSize() {
        if (mDirection == Direction.HORIZONTAL) {
            return getWidth();
        }
        return getHeight();
    }

    private boolean movePre() {
        if (this.getDataCount() <= 1) {
            return false;
        }
        mCurrentIndex--;
        mNext.recycle();
        mNext.copyFrom(mCurrent);
        mCurrent.copyFrom(mPre);
        mPre.attachAndLayout(mCurrentIndex - 1 ,true);
        return true;
    }

    private boolean moveNext() {
        if (this.getDataCount() <= 1) {
            return false;
        }
        mCurrentIndex++;
        mPre.recycle();
        mPre.copyFrom(mCurrent);
        mCurrent.copyFrom(mNext);
        mNext.attachAndLayout(mCurrentIndex + 1,true);
        return true;
    }

    private class PlayAnimationAction implements Runnable {

        OverScroller scroller = null;
        boolean isRunning = false;
        //boolean isStarted = false;

        PlayAnimationAction() {
            scroller = new OverScroller(LoopPlayerLayout.this.getContext(), new DecelerateInterpolator());
        }

        public void startMoveNext(long delay) {
            if (isCanAnimation()) {
                post(delay);
            }
        }

        public void startMoveNext() {
            startMoveNext(mWaitTime);
        }

        private void post(long delay) {
            LoopPlayerLayout.this.postOnAnimationDelayed(this, delay);
        }

        private void repost() {
            post(30);
        }

        public boolean isRunning() {
            return isRunning;
        }

        private void stop() {
            isRunning = false;
            this.abort();
            scrollInternal(scroller.getFinalX());
        }

        private void abort() {
            scroller.abortAnimation();
            LoopPlayerLayout.this.removeCallbacks(this);
        }

        private void resume() {
            if (!isCanAnimation()) {
                return;
            }
            scroller.startScroll(
                    getScrollOffset(),
                    0,
                    mCurrent.offset - getScrollOffset(),
                    0,
                    mAnimationTime);
            isRunning = true;
            repost();
        }

        private int getScrollOffset() {
            if (mDirection == Direction.HORIZONTAL) {
                return getScrollX();
            } else {
                return getScrollY();
            }
        }

        @Override
        public void run() {
            if (!isCanAnimation()) {
                return;
            }
            if (!isRunning) {
                abort();
                if (moveNext()) {
                    isRunning = true;
                    scroller.startScroll(
                            getScrollOffset(),
                            0,
                            mCurrent.offset - getScrollOffset(),
                            0,
                            mAnimationTime);
                    repost();
                    isRunning = true;
                    return;
                }
            }
            if (scroller.computeScrollOffset()) {
                int x = scroller.getCurrX();
                scrollInternal(x);
                if (x == scroller.getFinalX()) {
                    abort();
                    doAnimationEnd();
                } else {
                    repost();
                }
            } else {
                scrollInternal(scroller.getFinalX());
                doAnimationEnd();
            }
        }

        private void doAnimationEnd() {
            isRunning = false;
            mCurrent.onAnimationEnd();
            if (isCanAnimation()) {
                post(mWaitTime);
            }
        }
    }


    public enum Direction {
        VERTICAL,
        HORIZONTAL
    }

    public void setDirection(Direction direction) {
        if (this.mDirection != direction) {
            this.mDirection = direction;
            this.performDataChange();
        }
    }

    public Direction getDirection() {
        return this.mDirection;
    }


    public static abstract class LoopPlayAdapter extends BaseAdapter {

        @Override
        @Deprecated
        public final Object getItem(int i) {
            return null;
        }

        @Override
        @Deprecated
        public final long getItemId(int i) {
            return 0;
        }

        @Override
        public abstract View getView(int i, View view, ViewGroup viewGroup);
    }

    public LoopPlayerLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.init();
    }


    private void init() {
        mPlayAnimationAction = new PlayAnimationAction();
        mTouchSlop = ViewConfiguration.get(this.getContext()).getScaledTouchSlop();
    }

    @Override
    public LoopPlayerLayout.LoopPlayAdapter getAdapter() {
        return mBaseAdapter;
    }

    private void performDataChange() {
        this.clearData();
        this.fill();
        this.requestLayout();
    }

    private void fill() {
        mCurrent.attachAndLayout(mCurrentIndex,false);
        mPre.attachAndLayout(mCurrentIndex - 1,false);
        mNext.attachAndLayout(mCurrentIndex + 1,false);
    }

    private void clearData() {
        mPlayAnimationAction.stop();
        this.recycleAllViews();
        this.setScrollX(0);
        this.setScrollY(0);
        mCurrentIndex = 0;
        this.clearAllItems();
    }

    private void clearAllItems() {
        for (Item item : mAllItems) {
            item.clear();
        }
    }

    private void recycleAllViews() {
        int index = this.getChildCount() - 1;
        while (index >= 0) {
            recycleView(this.getChildAt(index));
            index--;
        }
        this.removeAllViewsInLayout();
    }

    private void recycleView(View view) {
        if (view == null) {
            return;
        }
        if (mRecyclerBin.size() < MAX_RECYCLER_NUMBER) {
            mRecyclerBin.add(view);
        }
    }

    private class DataSetObserverImpl extends DataSetObserver {
        @Override
        public void onChanged() {
            performDataChange();
        }
    }

    @Override
    public void setAdapter(LoopPlayerLayout.LoopPlayAdapter baseAdapter) {
        if (mBaseAdapter != baseAdapter) {
            if (mBaseAdapter != null) {
                mBaseAdapter.unregisterDataSetObserver(mDataSetObserverImpl);
            }
            mBaseAdapter = baseAdapter;
            if (mBaseAdapter != null) {
                mBaseAdapter.registerDataSetObserver(mDataSetObserverImpl);
                mBaseAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public View getSelectedView() {
        return null;
    }

    @Override
    public void setSelection(int i) {

    }

    public final int getDataCount() {
        if (mBaseAdapter == null) {
            return 0;
        }
        return mBaseAdapter.getCount();
    }


    public LoopPlayerLayout(Context context) {
        super(context);
        this.init();
    }


    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mPlayAnimationAction.stop();
    }

    private void performMove() {
        if (mDragStatus == DRAG_STATUS_NULL) {
            int offsetX = Math.abs(this.mStartMotionX - this.mMotionX) ;
            int offsetY = Math.abs(this.mStartMotionY - this.mMotionY);
            if (offsetX + offsetY >= mTouchSlop) {
                if (offsetX > offsetY) {
                    if (this.mDirection == Direction.HORIZONTAL) {
                        mDragStatus = DRAG_STATUS_ENABLE;
                    } else {
                        mDragStatus = DRAG_STATUS_DISABLE;
                    }
                } else {
                    if (this.mDirection == Direction.HORIZONTAL) {
                        mDragStatus = DRAG_STATUS_DISABLE;
                    } else {
                        mDragStatus = DRAG_STATUS_ENABLE;
                    }
                }
            }
            return;
        }
        if (mDragStatus == DRAG_STATUS_ENABLE) {
            if (this.mDirection == Direction.HORIZONTAL) {
                mFinalOffset = mMotionX - mStartMotionX;
                if (Math.abs(mFinalOffset) > getItemSize()) {
                    return;
                }
                scrollInternalBy(mPreMotionX - mMotionX);
            } else {
                mFinalOffset = mMotionY - mStartMotionY;
                if (Math.abs(mFinalOffset) > getItemSize()) {
                    return;
                }
                scrollInternalBy(mPreMotionY - mMotionY);

            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mDragStatus == DRAG_STATUS_DISABLE) {
            return false;
        }
        if (getDataCount() <= 1) {
            return false;
        }
        int action = ev.getActionMasked();
        mMotionX = (int)ev.getX();
        mMotionY = (int)ev.getY();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                this.getParent().requestDisallowInterceptTouchEvent(true);
                mPlayAnimationAction.abort();
                mStartMotionX = this.mMotionX;
                mStartMotionY = this.mMotionY;
                break;
            case MotionEvent.ACTION_MOVE:
                this.performMove();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mPlayAnimationAction.resume();
                mDragStatus = DRAG_STATUS_NULL;
                break;
        }
        mPreMotionX = this.mMotionX;
        mPreMotionY = this.mMotionY;
        return mDragStatus == DRAG_STATUS_ENABLE;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        mMotionX = (int)ev.getX();
        mMotionY = (int)ev.getY();
        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_MOVE:
                this.performMove();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                performUp();
                break;
        }
        mPreMotionX = this.mMotionX;
        mPreMotionY = this.mMotionY;
        return true;
    }

    private void performUp() {
        if (mDragStatus == DRAG_STATUS_ENABLE) {
            if (Math.abs(mFinalOffset) >= ( getItemSize() >> 2)) {
                if (mFinalOffset < 0) {//move next
                    moveNext();
                    mPlayAnimationAction.resume();
                } else {
                    //move pre
                    movePre();
                    mPlayAnimationAction.resume();
                }
            } else {
                mPlayAnimationAction.resume();
            }
        } else {
            mPlayAnimationAction.resume();
        }
        mDragStatus = DRAG_STATUS_NULL;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        this.setMeasuredDimension(width, height);

    }

    private void scrollInternal(int offset) {
        if (mDirection == Direction.HORIZONTAL) {
            if (this.getScrollX() != offset) {
                this.scrollTo(offset, 0);
                mScrollOffset = this.getScrollX();
            }
        } else {
            if (this.getScrollY() != offset) {
                this.scrollTo(0, offset);
                mScrollOffset = this.getScrollY();
            }
        }
    }

    private void scrollInternalBy(int dx) {
        scrollInternal(mScrollOffset + dx);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        for (Item item : mAllItems) {
            item.layoutAndLocation();
        }
        if (!mPlayAnimationAction.isRunning()
                && isCanAnimation()) {
            mPlayAnimationAction.startMoveNext();
        }
    }

    private boolean isCanAnimation() {
        return this.getWidth() > 0
                && this.getHeight() > 0
                && this.getVisibility() == View.VISIBLE
                && this.getDataCount() > 1;
    }


    public interface OnLoopPlayListener {
        void onPositionChange(int position, int count);
    }

    public void setOnLoopPlayListener(OnLoopPlayListener listener) {
        mOnLoopPlayListener = listener;
        if (mOnLoopPlayListener != null && mCurrentIndex != UNDEFINED) {
            mOnLoopPlayListener.onPositionChange(mCurrentIndex, this.getDataCount());
        }
    }
}
