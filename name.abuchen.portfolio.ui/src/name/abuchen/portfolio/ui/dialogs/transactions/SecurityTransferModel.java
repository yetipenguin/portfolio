package name.abuchen.portfolio.ui.dialogs.transactions;

import java.util.Date;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.PortfolioTransferEntry;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.Transaction;
import name.abuchen.portfolio.model.TransactionOwner;
import name.abuchen.portfolio.money.CurrencyConverter;
import name.abuchen.portfolio.money.CurrencyConverterImpl;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.snapshot.ClientSnapshot;
import name.abuchen.portfolio.snapshot.PortfolioSnapshot;
import name.abuchen.portfolio.snapshot.SecurityPosition;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.util.Dates;

import org.eclipse.core.databinding.validation.ValidationStatus;
import org.eclipse.core.runtime.IStatus;

import com.ibm.icu.text.MessageFormat;

public class SecurityTransferModel extends AbstractModel
{
    public enum Properties
    {
        security, securityCurrencyCode, sourcePortfolio, sourcePortfolioLabel, targetPortfolio, targetPortfolioLabel, date, shares, quote, amount, note, calculationStatus;
    }

    private final Client client;

    private PortfolioTransferEntry source;

    private Security security;
    private Portfolio sourcePortfolio;
    private Portfolio targetPortfolio;
    private Date date = Dates.today();

    private long shares;
    private long quote;
    private long amount;
    private String note;

    private IStatus calculationStatus = ValidationStatus.ok();

    public SecurityTransferModel(Client client)
    {
        this.client = client;
    }

    @Override
    public String getHeading()
    {
        return Messages.AccountMenuTransfer;
    }

    @Override
    public void applyChanges()
    {
        if (security == null)
            throw new UnsupportedOperationException(Messages.MsgMissingSecurity);
        if (sourcePortfolio == null)
            throw new UnsupportedOperationException(Messages.MsgPortfolioFromMissing);
        if (targetPortfolio == null)
            throw new UnsupportedOperationException(Messages.MsgPortfolioToMissing);

        PortfolioTransferEntry t;

        if (source != null && sourcePortfolio.equals(source.getOwner(source.getSourceTransaction()))
                        && targetPortfolio.equals(source.getOwner(source.getTargetTransaction())))
        {
            // transaction stays in same accounts
            t = source;
        }
        else
        {
            if (source != null)
            {
                @SuppressWarnings("unchecked")
                TransactionOwner<Transaction> owner = (TransactionOwner<Transaction>) source.getOwner(source
                                .getSourceTransaction());
                owner.deleteTransaction(source.getSourceTransaction(), client);
                source = null;
            }

            t = new PortfolioTransferEntry(sourcePortfolio, targetPortfolio);
            t.insert();
        }

        t.setSecurity(security);
        t.setDate(date);
        t.setShares(shares);
        t.setAmount(amount);
        t.setCurrencyCode(security.getCurrencyCode());
        t.setNote(note);
    }

    private IStatus calculateStatus()
    {
        // check whether lump sum is in range
        long lower = Math.round(shares * (quote - 1) * Values.Amount.factor()
                        / (Values.Share.divider() * Values.Quote.divider()));
        long upper = Math.round(shares * (quote + 1) * Values.Amount.factor()
                        / (Values.Share.divider() * Values.Quote.divider()));
        if (amount < lower || amount > upper)
            return ValidationStatus.error(Messages.MsgIncorrectSubTotal);

        if (amount == 0L)
            return ValidationStatus.error(MessageFormat.format(Messages.MsgDialogInputRequired, Messages.ColumnAmount));

        return ValidationStatus.ok();
    }

    private void updateSharesAndQuote()
    {
        SecurityPosition position = null;

        if (security != null)
        {
            CurrencyConverter converter = new CurrencyConverterImpl(getExchangeRateProviderFactory(),
                            client.getBaseCurrency());
            PortfolioSnapshot snapshot = sourcePortfolio != null ? PortfolioSnapshot.create(sourcePortfolio, converter,
                            date) : ClientSnapshot.create(client, converter, date).getJointPortfolio();
            position = snapshot.getPositionsBySecurity().get(security);
        }

        if (position != null)
        {
            setShares(position.getShares());
            // setAmount will also set quote
            setAmount(position.calculateValue().getAmount());
        }
        else if (security != null)
        {
            setShares(0);
            setQuote(security.getSecurityPrice(date).getValue());
        }
        else
        {
            setShares(0);
            setQuote(0);
        }
    }

    public void setSource(PortfolioTransferEntry entry)
    {
        this.source = entry;
        this.sourcePortfolio = (Portfolio) entry.getOwner(entry.getSourceTransaction());
        this.targetPortfolio = (Portfolio) entry.getOwner(entry.getTargetTransaction());

        this.security = entry.getSourceTransaction().getSecurity();
        this.date = entry.getSourceTransaction().getDate();
        this.shares = entry.getSourceTransaction().getShares();
        this.quote = entry.getSourceTransaction().getActualPurchasePrice();
        this.amount = entry.getTargetTransaction().getAmount();
        this.note = entry.getSourceTransaction().getNote();
    }

    public IStatus getCalculationStatus()
    {
        return calculationStatus;
    }

    public Security getSecurity()
    {
        return security;
    }

    public void setSecurity(Security security)
    {
        String oldCurrencyCode = getSecurityCurrencyCode();
        firePropertyChange(Properties.security.name(), this.security, this.security = security);
        firePropertyChange(Properties.securityCurrencyCode.name(), oldCurrencyCode, getSecurityCurrencyCode());

        updateSharesAndQuote();
    }

    public Portfolio getSourcePortfolio()
    {
        return sourcePortfolio;
    }

    public void setSourcePortfolio(Portfolio portfolio)
    {
        String oldLabel = getSourcePortfolioLabel();
        firePropertyChange(Properties.sourcePortfolio.name(), this.sourcePortfolio, this.sourcePortfolio = portfolio);
        firePropertyChange(Properties.sourcePortfolioLabel.name(), oldLabel, getSourcePortfolioLabel());

        updateSharesAndQuote();
    }

    public String getSourcePortfolioLabel()
    {
        return sourcePortfolio != null ? sourcePortfolio.getReferenceAccount().getName() : ""; //$NON-NLS-1$
    }

    public Portfolio getTargetPortfolio()
    {
        return targetPortfolio;
    }

    public void setTargetPortfolio(Portfolio portfolio)
    {
        String oldLabel = getTargetPortfolioLabel();
        firePropertyChange(Properties.targetPortfolio.name(), this.targetPortfolio, this.targetPortfolio = portfolio);
        firePropertyChange(Properties.targetPortfolioLabel.name(), oldLabel, getTargetPortfolioLabel());
    }

    public String getTargetPortfolioLabel()
    {
        return targetPortfolio != null ? targetPortfolio.getReferenceAccount().getName() : ""; //$NON-NLS-1$
    }

    public Date getDate()
    {
        return date;
    }

    public void setDate(Date date)
    {
        firePropertyChange(Properties.date.name(), this.date, this.date = date);
        updateSharesAndQuote();
    }

    public long getShares()
    {
        return shares;
    }

    public void setShares(long shares)
    {
        firePropertyChange(Properties.shares.name(), this.shares, this.shares = shares);

        if (quote != 0)
        {
            setAmount(Math.round(shares * quote * Values.Amount.factor()
                            / (Values.Share.divider() * Values.Quote.divider())));
        }
        else if (amount != 0 && shares != 0)
        {
            setQuote(Math.round(amount * Values.Share.factor() / shares));
        }

        firePropertyChange(Properties.calculationStatus.name(), this.calculationStatus,
                        this.calculationStatus = calculateStatus());
    }

    public long getQuote()
    {
        return quote;
    }

    public void setQuote(long quote)
    {
        firePropertyChange(Properties.quote.name(), this.quote, this.quote = quote);

        triggerAmount(Math.round(shares * quote * Values.Amount.factor()
                        / (Values.Share.divider() * Values.Quote.divider())));

        firePropertyChange(Properties.calculationStatus.name(), this.calculationStatus,
                        this.calculationStatus = calculateStatus());
    }

    public long getAmount()
    {
        return amount;
    }

    public void setAmount(long amount)
    {
        triggerAmount(amount);

        if (shares != 0)
        {
            long newQuote = Math.round(amount * Values.Share.factor() / shares);
            firePropertyChange(Properties.quote.name(), this.quote, this.quote = newQuote);
        }

        firePropertyChange(Properties.calculationStatus.name(), this.calculationStatus,
                        this.calculationStatus = calculateStatus());
    }

    public void triggerAmount(long amount)
    {
        firePropertyChange(Properties.amount.name(), this.amount, this.amount = amount);
    }

    public String getNote()
    {
        return note;
    }

    public void setNote(String note)
    {
        firePropertyChange(Properties.note.name(), this.note, this.note = note);
    }

    public String getSecurityCurrencyCode()
    {
        return security != null ? security.getCurrencyCode() : ""; //$NON-NLS-1$
    }
}
