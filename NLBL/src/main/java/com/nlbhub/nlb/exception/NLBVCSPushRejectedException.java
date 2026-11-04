package com.nlbhub.nlb.exception;

/**
 * Сервер не принял push: на нём есть коммиты, которых у автора нет (соавтор успел отправить
 * свои правки). Выход — получить их (pull) и отправить снова. ofBook — отклонена сама книга
 * (не внешний модуль: его правки с сервера pull книги не сливает)
 */
public class NLBVCSPushRejectedException extends NLBVCSException {
    private final boolean m_ofBook;

    public NLBVCSPushRejectedException(String message, boolean ofBook) {
        super(message);
        m_ofBook = ofBook;
    }

    public boolean isOfBook() {
        return m_ofBook;
    }
}
